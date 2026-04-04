# Plugin Signing — Setup Guide

This folder contains the signing tools and credentials for publishing to JetBrains Marketplace.
All files except this README are git-ignored.

## Prerequisites

- `openssl` (available via Homebrew: `brew install openssl`)

## Generate signing keys (one-time)

```bash
cd cert

# 1. Generate private key
openssl genrsa -out private.pem 4096

# 2. Generate self-signed certificate (valid 10 years)
openssl req -new -x509 -key private.pem -out certificate-chain.crt -days 3650 \
  -subj "/C=IT/CN=ToolbarLauncher"

# 3. Encrypt the private key with a password
openssl pkcs8 -topk8 -inform PEM -outform PEM \
  -in private.pem -out private-encrypted.pem -passout pass:yourpassword
```

Replace `yourpassword` with a strong password and keep it safe.

## Add secrets to GitHub

Go to **GitHub → Settings → Secrets and variables → Actions** and add:

| Secret | File |
|---|---|
| `CERTIFICATE_CHAIN` | contents of `certificate-chain.crt` |
| `PRIVATE_KEY` | contents of `private-encrypted.pem` |
| `PRIVATE_KEY_PASSWORD` | the password chosen above |
| `PUBLISH_TOKEN` | JetBrains Marketplace → your profile → Tokens |

```bash
# Quick copy to clipboard (macOS)
cat certificate-chain.crt | pbcopy   # paste as CERTIFICATE_CHAIN
cat private-encrypted.pem | pbcopy   # paste as PRIVATE_KEY
```

## Files in this folder

| File | Description |
|---|---|
| `private.pem` | Unencrypted private key — delete after step 3 |
| `private-encrypted.pem` | Encrypted private key → `PRIVATE_KEY` secret |
| `certificate-chain.crt` | Certificate chain → `CERTIFICATE_CHAIN` secret |
