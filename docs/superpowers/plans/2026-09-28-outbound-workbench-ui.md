# Outbound Operations Workbench UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the basic SIM call test page with a responsive OpenChatCut-inspired operations workbench backed entirely by the existing outbound task, event, turn, and SSE APIs.

**Architecture:** Keep the Spring Boot static-resource delivery model and implement the workbench with semantic HTML, CSS design tokens, and framework-free JavaScript. Separate concerns within `app.js` into data loading, derived metrics, renderers, dialog control, and SSE connectivity while preserving explicit call confirmation.

**Tech Stack:** HTML5, CSS3, vanilla JavaScript, Spring Boot static resources, JUnit 5, MockMvc.

---

### Task 1: Workbench semantic skeleton

**Files:**
- Modify: `src/test/java/com/company/outbound/system/StaticPageTest.java`
- Modify: `src/main/resources/static/index.html`
- Modify: `.gitignore`

- [ ] **Step 1: Add a failing static page structure test**

Assert that `/index.html` contains `CallFlow Studio`, `operations-shell`, `metricGrid`, `callStage`, `conversationPanel`, `dialogTimeline`, and the explicit authorization text.

- [ ] **Step 2: Run the focused test and verify RED**

Run `mvn -q -Dtest=StaticPageTest#servesOperationsWorkbench test` and expect failure because `CallFlow Studio` is absent.

- [ ] **Step 3: Replace the page markup with the semantic workbench skeleton**

Create a top bar, rail navigation, task browser, metric cards, call stage, AI conversation panel, timeline, call dialog, toast region, and accessible labels. Keep existing DOM identifiers only when they remain meaningful.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run `mvn -q -Dtest=StaticPageTest test` and expect all `StaticPageTest` cases to pass.

- [ ] **Step 5: Commit the structure**

Commit `.gitignore`, `index.html`, the static test, and this plan with message `feat(ui): add outbound workbench structure`.

### Task 2: Visual design system and responsive layout

**Files:**
- Modify: `src/test/java/com/company/outbound/system/StaticPageTest.java`
- Modify: `src/main/resources/static/styles.css`

- [ ] **Step 1: Add a failing stylesheet contract test**

Assert that `/styles.css` includes `--accent-mint`, `.workspace-grid`, `.call-wave`, `@keyframes`, `prefers-reduced-motion`, and responsive rules.

- [ ] **Step 2: Run the focused test and verify RED**

Run `mvn -q -Dtest=StaticPageTest#servesWorkbenchDesignSystem test` and expect failure on `--accent-mint`.

- [ ] **Step 3: Implement the complete visual system**

Define dark industrial tokens, typography, rail and panel layouts, status badges, metric cards, wave animation, transcript bubbles, horizontal timeline, dialog, toast, focus states, reduced-motion handling, and desktop/tablet/mobile breakpoints.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run `mvn -q -Dtest=StaticPageTest test` and expect all static tests to pass.

- [ ] **Step 5: Commit the visual system**

Commit CSS and its test with message `feat(ui): style responsive call operations console`.

### Task 3: Real task data, metrics, conversation, and timeline

**Files:**
- Modify: `src/test/java/com/company/outbound/system/StaticPageTest.java`
- Modify: `src/main/resources/static/app.js`

- [ ] **Step 1: Add failing client behavior contract tests**

Assert that `/app.js` contains pure helpers `deriveMetrics`, `statusTone`, `mask`, renderers `renderMetrics`, `renderCallStage`, `renderConversation`, `renderTimeline`, and uses all existing task/event/turn endpoints.

- [ ] **Step 2: Run the focused test and verify RED**

Run `mvn -q -Dtest=StaticPageTest#clientRendersWorkbenchFromRealApis test` and expect failure because `deriveMetrics` is absent.

- [ ] **Step 3: Implement data derivation and renderers**

Load tasks, compute metrics from real records, apply search/status filters, render masked task rows, populate the call stage, render turn transcript/intent/confidence cards, and map backend events to timeline nodes without creating mock production data.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run `mvn -q -Dtest=StaticPageTest test` and expect all static tests to pass.

- [ ] **Step 5: Commit real-data rendering**

Commit JavaScript and its test with message `feat(ui): render live call intelligence and timeline`.

### Task 4: Safe call dialog, SSE state, and error feedback

**Files:**
- Modify: `src/test/java/com/company/outbound/system/StaticPageTest.java`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/index.html` if accessibility wiring requires it

- [ ] **Step 1: Add failing safety interaction tests**

Assert that the client requires `confirmed`, calls `/start-dialog`, exposes `openCallDialog`/`closeCallDialog`, uses `showToast`, reconnects SSE with bounded backoff, and never automatically redials.

- [ ] **Step 2: Run the focused test and verify RED**

Run `mvn -q -Dtest=StaticPageTest#clientKeepsExplicitCallConfirmationAndVisibleErrors test` and expect failure on `openCallDialog`.

- [ ] **Step 3: Implement safe interactions**

Wire dialog open/close and Escape handling, validate one 11-digit number and authorization, create/start the task only on submit, provide non-blocking toast errors, disable unavailable takeover/hangup buttons with explanatory tooltips, and update connection state through SSE.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run `mvn -q -Dtest=StaticPageTest test` and expect all static tests to pass.

- [ ] **Step 5: Commit safe interactions**

Commit the affected files with message `feat(ui): add safe call controls and realtime feedback`.

### Task 5: Browser verification, documentation, and publish

**Files:**
- Modify: `README.md`
- Create: `docs/screenshots/outbound-workbench.png`

- [ ] **Step 1: Run the full test suite**

Run `mvn -q test` and require exit code 0.

- [ ] **Step 2: Start the application in mock mode and inspect the browser**

Run `mvn spring-boot:run`, open `http://localhost:8080`, verify desktop and narrow layouts, call dialog behavior, empty state, focus visibility, and browser console errors.

- [ ] **Step 3: Capture the verified workbench**

Save a screenshot to `docs/screenshots/outbound-workbench.png` and add it to the README product section.

- [ ] **Step 4: Commit verification evidence and documentation**

Commit screenshot and README with message `docs: showcase outbound operations workbench`.

- [ ] **Step 5: Push the genuine commit series**

Run `git push whitekk HEAD:main` without force and verify the remote `main` hash equals local `HEAD`.
