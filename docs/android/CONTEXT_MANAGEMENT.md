# Context Management - Architecture Decision

## Problem
The Qwen 0.5B model has a **512-token context window**. Once filled, it can't generate new tokens.

## Current Status (After Testing)
- ✅ **First message**: 63 tokens (native app), 67 tokens (server)  
- ✅ **Second message**: 19-23 tokens
- ❌ **Third+ messages**: 0 tokens (context full)

## Root Cause
The llama.cpp `g_n_past` counter accumulates across all messages. When it reaches the context limit (~512 tokens including prompt + conversation), generation stops.

## Solution Options

### Option 1: Full Native App (RECOMMENDED)
**Best for:** Personal AI assistant with conversation memory

**Architecture:**
```
User Message 1 → [System Prompt + User 1] → Response 1
User Message 2 → [System Prompt + User 1 + Response 1 + User 2] → Response 2
User Message 3 → [System Prompt + ... full history ... + User 3] → Response 3
```

**Pros:**
- ✅ Natural multi-turn conversations
- ✅ Model remembers context from earlier messages
- ✅ Better user experience (like ChatGPT)
- ✅ Efficient (no repeated tokenization)

**Cons:**
- ⚠️ Context fills up after ~3-4 exchanges (512 tokens total)
- ⚠️ Need "New Chat" button to clear context
- ⚠️ Need UI indicator showing context usage

**Implementation:**
1. Keep `g_n_past` accumulating across messages ✅ (already done)
2. Only clear context when user taps "New Chat" button
3. Add context usage indicator: "Context: 234/512 tokens"
4. Optionally: Implement sliding window (keep last N messages)

**Code Changes Needed:**
```kotlin
// NativeChatActivity.kt
- Add "New Chat" FAB button
- Call engine.clearContext() on new chat
- Show context usage: "${engine.getContextUsage()}/512 tokens"
```

```cpp
// llama_jni.cpp  
- Add getContextUsage() → returns g_n_past
- Keep current clearContext() implementation
```

---

### Option 2: Stateless Server Mode (CURRENT)
**Best for:** Multiple clients, each request independent

**Architecture:**
```
Client 1 Request → [Clear] → [System Prompt + Message] → Response → [Clear]
Client 2 Request → [Clear] → [System Prompt + Message] → Response → [Clear]
```

**Pros:**
- ✅ Every request starts fresh (no accumulated context)
- ✅ Multiple clients don't interfere
- ✅ Simple to understand
- ✅ No "full context" errors

**Cons:**
- ❌ No conversation memory (each message is independent)
- ❌ Can't reference previous responses
- ❌ Less natural UX ("Who are you?" → "What else?" won't work)

**Implementation:**
- **Already done!** Server calls `engine.clearContext()` before each request
- Works fine for single-shot Q&A

---

### Option 3: Hybrid Approach
**Best for:** Production apps with both modes

**Architecture:**
```
Native App: Persistent context with "New Chat" button
Server API: Session-based context management
  - POST /api/chat?session_id=abc → keeps context per session
  - DELETE /api/session/abc → clears that session
```

**Pros:**
- ✅ Best of both worlds
- ✅ Native app has conversation memory
- ✅ Server supports multi-turn conversations via sessions
- ✅ Production-ready

**Cons:**
- ⚠️ More complex implementation
- ⚠️ Need session storage (HashMap<sessionId, context>)
- ⚠️ Memory management (cleanup old sessions)

---

## Recommendation

### For Your Use Case: **Option 1 (Native App)**

**Why:**
1. You're using this personally (not serving many clients)
2. Conversation memory is valuable ("who are you?" → "what else?")
3. Hotspot mode works fine for showing to others (they get fresh context each time)
4. Simple to implement (just add "New Chat" button)

### Implementation Plan:

1. **Remove server-side clearContext()** (let native app keep context):
```kotlin
// AlfredServer.kt - Remove this line:
engine.clearContext()  // ← DELETE THIS for server to have memory too
```

2. **Add "New Chat" button to native app**:
```kotlin
// Add floating action button
FloatingActionButton(
    onClick = { 
        engine.clearContext()
        messages.clear()
        messages.add(ChatMessage("Hello! How can I help you?", isUser = false))
    }
) {
    Icon(Icons.Default.Add, "New Chat")
}
```

3. **Add context usage indicator** (optional but helpful):
```kotlin
// Top bar status
Text("Context: ${messages.sumOf { it.text.split(" ").size * 2 }}/512 tokens")
```

4. **Auto-clear when full** (prevents 0-token issue):
```kotlin
// In LlamaEngine.kt, before generate():
if (g_n_past > 450) {  // Leave some buffer
    Log.w(TAG, "Context nearly full, auto-clearing")
    clearContext()
}
```

---

## Why We're Seeing 0 Tokens

The current hybrid code is **inconsistent**:
- ✅ Server mode: Clears context before each request (works for 1 message, then fails)
- ❌ Native app: Doesn't clear, but also doesn't handle full context

**The fix:**
1. Decide: Do we want conversation memory? YES → Option 1
2. Remove `clearContext()` from server (or make it optional)
3. Add manual "New Chat" button
4. Monitor context usage

---

## Testing Results Summary

### Session 1 (Old Code - No clearing):
```
Message 1: "How does water cycle work" → 63 tokens ✅
Message 2: "okay than you" → 19 tokens ✅
Message 3: "sure I will c..." → 0 tokens ❌ (context full)
```

### Session 2 (New Code - Server clearing):
```
GET / → HTML loaded ✅
Message 1: "Explain photosynthesis" → 67 tokens ✅
Message 2: "who are you" → 23 tokens ✅
Message 3: "okay what else" → 0 tokens ❌ (still broken)
```

**Why still broken?**
The server clears context, but then the native app uses the **same engine instance** which still has `g_n_past > 0` from previous native app usage!

---

## The Real Fix

**Root issue:** Shared engine instance between native app and server

**Solution A - Separate instances:**
```kotlin
private val nativeEngine = LlamaEngine()
private val serverEngine = LlamaEngine()  // Separate instance for server
```

**Solution B - Always clear for server, never for native:**
```kotlin
// AlfredServer.kt
engine.clearContext()  // Keep this for server

// NativeChatActivity.kt  
// Never auto-clear, only on user action
```

**Solution C (Recommended) - Context management flags:**
```cpp
// Add mode parameter
JNIEXPORT jboolean JNICALL
Java_org_ekatra_alfred_LlamaEngine_nativePrepare(
        JNIEnv* env, jobject thiz, jstring prompt_text, jboolean clear_context) {
    if (clear_context) {
        llama_kv_cache_clear(g_ctx);
        g_n_past = 0;
    }
    // ... rest of code
}
```

Then:
```kotlin
// Server mode
engine.generate(message, clearBefore = true)

// Native app
engine.generate(message, clearBefore = false)
```

---

## Immediate Action

**Try this quick test:**
1. Force-stop app completely
2. Open fresh
3. Test only in server mode (from browser)
4. Should work for multiple messages now!

**Why?** Fresh app start means `g_n_past = 0`, so first server request will clear properly.

