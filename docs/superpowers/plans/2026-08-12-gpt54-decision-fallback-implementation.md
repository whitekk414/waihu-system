# GPT-5.4 Decision Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect dialog decisions to the UAT GPT-5.4 endpoint with strict validation and local-rule fallback.

**Architecture:** `GptDecisionAdapter` owns the UAT HTTP contract and validated model response. `FallbackDecisionAdapter` composes GPT as primary and `RuleDecisionAdapter` as fallback so telephony orchestration remains independent of provider failures.

**Tech Stack:** Java 17, Spring Boot 3.4, Java HttpClient, Jackson, JUnit 5, local UAT HTTP endpoint.

---

### Task 1: Lock the UAT HTTP contract

**Files:**
- Modify: `src/test/java/com/company/outbound/processing/GptDecisionAdapterTest.java`
- Modify: `src/main/java/com/company/outbound/processing/GptDecisionAdapter.java`
- Modify: `src/main/resources/application.yml`

- [ ] Add a failing HTTP-server test asserting `X-LLM-Env`, `X-Request-Id`, `tenant_id`, `model=openai.gpt-5.4`, and the exact configured endpoint.
- [ ] Run `mvn -Dtest=GptDecisionAdapterTest test` and verify the contract test fails.
- [ ] Add configurable endpoint, environment, tenant and 15-second timeout; remove mandatory bearer authentication.
- [ ] Run the focused test and verify it passes.

### Task 2: Add deterministic local fallback

**Files:**
- Create: `src/main/java/com/company/outbound/processing/FallbackDecisionAdapter.java`
- Create: `src/test/java/com/company/outbound/processing/FallbackDecisionAdapterTest.java`
- Modify: `src/main/java/com/company/outbound/processing/GptDecisionAdapter.java`
- Modify: `src/main/java/com/company/outbound/processing/RuleDecisionAdapter.java`

- [ ] Add failing tests proving primary success is returned and every primary runtime failure invokes the rule adapter exactly once.
- [ ] Run the focused test and verify RED.
- [ ] Make GPT and rule adapters provider-specific components and expose one `@Primary DecisionPort` fallback composition in GPT mode.
- [ ] Run focused tests and verify GREEN.

### Task 3: Validate locally and against UAT

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] Run `mvn test` and require zero failures.
- [ ] Run `git diff --check` and inspect `git status --short`; do not commit.
- [ ] Send the prior non-sensitive transcript to GPT-5.4 and verify a valid constrained JSON decision without making a phone call.
