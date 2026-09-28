# Project Documentation and Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete business-facing and engineering-facing project documentation, verify the repository contents, and publish the full safe source tree to `whitekk414/waihu-system`.

**Architecture:** Use `README.md` as the shared project entrance, `docs/DEPLOYMENT.md` as the environment and installation runbook, and `docs/TESTING.md` as the repeatable verification and acceptance runbook. Keep runtime data and device-specific identifiers out of Git through `.gitignore` and placeholder-based configuration.

**Tech Stack:** Markdown, Java 17, Spring Boot 3.4, Maven, Python 3, FastAPI, SenseVoiceSmall, Asterisk 18 ARI, WSL2, chan_mobile, Git.

---

### Task 1: Create the shared project overview

**Files:**
- Create: `README.md`

- [ ] **Step 1: Document the business status and supported flow**

Describe the proven SIM outbound path, prerecorded multi-turn guidance, per-turn recording, SenseVoice recognition, deterministic intent routing, retry, and manual-follow-up marker.

- [ ] **Step 2: Document architecture and repository structure**

Show the data path from Spring Boot through ARI/Asterisk and Bluetooth to the phone, and link the deployment and testing runbooks.

- [ ] **Step 3: Add safe quick-start commands**

Provide mock-mode commands that do not initiate a real phone call, then point real-device users to `docs/DEPLOYMENT.md`.

### Task 2: Create the deployment runbook

**Files:**
- Create: `docs/DEPLOYMENT.md`
- Reference: `.env.example`
- Reference: `scripts/install-wsl-asterisk-config.ps1`
- Reference: `speech-service/README.md`

- [ ] **Step 1: List prerequisites and environment variables**

Document JDK 17, Maven, Python, WSL2 Ubuntu, Asterisk 18, usbipd-win, CSR8510-compatible Bluetooth hardware, Android Bluetooth pairing, and all required environment variables.

- [ ] **Step 2: Document service installation and startup order**

Install and check Asterisk/chan_mobile, start SenseVoice on port 8090, package Java, and run the application on port 8081 using real ARI and transcription settings.

- [ ] **Step 3: Add security and data-handling requirements**

Require explicit dial authorization, legal recording notice, secret injection through environment variables, access controls, retention policy, and no customer recordings in Git.

### Task 3: Create the testing and acceptance runbook

**Files:**
- Create: `docs/TESTING.md`

- [ ] **Step 1: Add automated verification commands**

Run `mvn -q test` and `speech-service/.venv/Scripts/python -m pytest -q speech-service/tests` with expected successful exit codes.

- [ ] **Step 2: Add non-call and real-call acceptance checklists**

Verify services, ARI, Bluetooth device state, prompts, call authorization, three guided questions, recordings, transcripts, intent results, retries, and manual-follow-up output.

- [ ] **Step 3: Add troubleshooting guidance**

Cover no audio, device not free, ARI authentication, missing recording, model not ready, and unclear answer handling.

### Task 4: Verify, commit, and publish

**Files:**
- Modify: `.gitignore` only if the final staged-content scan finds a missing runtime exclusion.

- [ ] **Step 1: Verify documentation links and repository hygiene**

Run Markdown path checks, `git diff --check`, and staged scans for logs, recordings, model weights, virtual environments, phone numbers, MAC addresses, and credentials.

- [ ] **Step 2: Run complete automated tests**

Run the Java and Python test suites and require exit code 0 for both.

- [ ] **Step 3: Commit documentation**

```powershell
git add README.md docs/DEPLOYMENT.md docs/TESTING.md docs/superpowers/plans/2026-09-28-project-documentation-and-publishing.md
git commit -m "docs: add project delivery and deployment guides"
```

- [ ] **Step 4: Fetch and publish without overwriting remote history**

```powershell
git fetch whitekk --prune
git push -u whitekk HEAD:main
```

Expected: the remote `main` branch points to the local documentation commit. If the remote contains unrelated commits, stop rather than force-push.
