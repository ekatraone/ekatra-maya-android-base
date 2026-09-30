# Privacy Policy for Maya AI

**Last Updated:** February 4, 2026  
**Effective Date:** February 4, 2026

## Introduction

Maya AI ("we," "our," or "the app") is an offline educational AI assistant developed by Ekatra. This Privacy Policy explains how we handle information when you use our Android application.

**Our Core Principle: Your data stays on your device.**

## Summary

| Data Type | Collected? | Shared? | Stored Where? |
|-----------|------------|---------|---------------|
| Conversations | Yes | No | Your device only |
| Personal info | No | No | N/A |
| Location | No | No | N/A |
| Analytics | No | No | N/A |
| Usage tracking | No | No | N/A |

## Information We Do NOT Collect

Maya AI is designed with privacy as a core principle. We do **NOT** collect, store, or transmit:

- ❌ Personal identification information
- ❌ Email addresses or phone numbers
- ❌ Location data
- ❌ Device identifiers
- ❌ Usage analytics or telemetry
- ❌ Crash reports to external servers
- ❌ Advertising identifiers
- ❌ Any data to third parties

## Information Stored Locally

The following information is stored **only on your device** and never transmitted:

### Conversation History
- Your chat messages with Maya AI
- AI responses
- Stored in local SQLite database
- You can clear this anytime from Settings

### AI Model
- Downloaded once during initial setup
- Stored in app's private storage
- Used for on-device AI processing

### App Preferences
- Dark mode settings
- Server mode preferences
- Stored in Android SharedPreferences

## Network Usage

Maya AI uses network connectivity for:

1. **Initial Model Download** (one-time)
   - Downloads AI model from Hugging Face
   - No personal data transmitted
   - Can work fully offline after download

2. **Multi-Device Server Mode** (optional)
   - If enabled, creates local network server
   - Only accessible on your local WiFi
   - No internet transmission

## AI Processing

All AI inference happens **entirely on your device**:

- Conversations are processed locally
- No cloud AI services used
- No conversation data leaves your device
- Works completely offline

## Children's Privacy

Maya AI is designed for educational use by students of all ages. Because we collect no personal information, the app is compliant with COPPA (Children's Online Privacy Protection Act) and similar regulations.

## Data Security

- All data stored in Android's private app directory
- Protected by Android's sandboxing
- No external access possible
- Data deleted when app is uninstalled

## Your Rights

You have full control over your data:

- **Access**: View all conversations in the app
- **Delete**: Clear history from Settings
- **Export**: Copy/share individual conversations
- **Uninstall**: Removes all app data

## Third-Party Services

Maya AI uses the following open-source components:

| Component | Purpose | Privacy Impact |
|-----------|---------|----------------|
| llama.cpp | AI inference | None - runs locally |
| Qwen model | Language model | None - runs locally |
| NanoHTTPD | Local server | Local network only |
| Room/SQLite | Data storage | Local only |

No third-party analytics, advertising, or tracking services are used.

## Changes to This Policy

We may update this Privacy Policy. Changes will be:
- Posted in the app
- Noted in the changelog
- Effective immediately upon posting

## Open Source

Maya AI is built with transparency. Our source code is available for review.

## Contact Us

For privacy questions or concerns:

- **Email**: privacy@ekatra.org
- **GitHub**: https://github.com/ekatra

## Legal Basis (GDPR)

For users in the European Economic Area:
- We do not process personal data
- No consent required as no data collection occurs
- No data transfers outside your device

---

## Future Features (v1.1+)

### Optional User Profile

In future versions, Maya AI will offer **optional** user registration:

| Feature | Data | Storage | User Control |
|---------|------|---------|--------------|
| Profile name | Your name | Local first, optional sync | You choose to sync |
| Profile photo | Your photo | Local first, optional sync | You choose to sync |
| Usage stats | Questions asked, subjects | Local first, optional sync | You choose to sync |

**Key Privacy Principles for User Profiles:**
- Works 100% offline without any registration
- Registration is always optional
- All data stored locally first
- You explicitly choose to sync to Ekatra cloud
- You can delete your cloud data anytime
- No data shared with third parties

When these features are released, this privacy policy will be updated with full details.

---

**Summary**: Maya AI is a privacy-first application. All your conversations and data stay on your device. We have no servers collecting your information. When you delete the app, all data is permanently removed.
