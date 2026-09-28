const state = { tasks: [], selectedId: null, reconnectDelay: 1000, query: "", statusFilter: "ALL", detail: { task: null, events: [], turns: [] } };
const $ = id => document.getElementById(id);
const ACTIVE_STATUSES = new Set(["VALIDATING", "ORIGINATING", "RINGING", "ANSWERED", "PLAYING_PROMPT", "WAITING_RESPONSE", "CALL_ENDED", "RECORDING_READY", "TRANSCRIBING", "ANALYZING"]);
const STATUS_LABELS = { PENDING: "等待开始", VALIDATING: "校验号码", ORIGINATING: "正在拨号", RINGING: "等待接听", ANSWERED: "已接通", PLAYING_PROMPT: "播放话术", WAITING_RESPONSE: "等待回答", CALL_ENDED: "通话结束", RECORDING_READY: "录音就绪", TRANSCRIBING: "语音识别", ANALYZING: "意图判断", COMPLETED: "已完成", FAILED: "失败" };
const NODE_LABELS = { ASK_IDENTITY: "确认客户身份", ASK_HOME_VISIT: "确认家访意愿", EXPLAIN_AGENCY: "说明家访机构", EXPLAIN_CONTACT_TIME: "说明联系时间", EXPLAIN_COOPERATION: "说明配合事项", CLOSING: "结束说明" };

async function api(path, options = {}) {
  const response = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
  const text = await response.text();
  const contentType = response.headers.get("content-type") || "";
  let body = null;
  if (!text.trim() && response.ok) return null;
  if (!text.trim()) throw new Error(`HTTP ${response.status}`);
  if (contentType.includes("application/json")) { try { body = JSON.parse(text); } catch (_) { body = null; } }
  if (!response.ok) throw new Error(body?.message || body?.detail || text || `HTTP ${response.status}`);
  return body ?? text;
}

function mask(number = "") { const value = String(number); return /^1\d{10}$/.test(value) ? `${value.slice(0, 3)}****${value.slice(-4)}` : value; }
function formatTime(value) { if (!value) return "—"; const date = new Date(value); return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("zh-CN", { hour12: false }); }
function statusTone(status) { if (status === "COMPLETED") return "success"; if (status === "FAILED") return "danger"; return ACTIVE_STATUSES.has(status) ? "active" : "neutral"; }
function needsManual(task) { if (!task?.analysisJson) return false; try { return JSON.parse(task.analysisJson)?.needManualFollowUp === true; } catch (_) { return false; } }
function deriveMetrics(tasks) { const total = tasks.length; const completed = tasks.filter(task => task.status === "COMPLETED").length; const failed = tasks.filter(task => task.status === "FAILED").length; const manual = tasks.filter(needsManual).length; return { total, completed, failed, manual, rate: total ? Math.round(completed * 100 / total) : 0 }; }

function renderMetrics() {
  const metrics = deriveMetrics(state.tasks);
  $("metricTotal").textContent = metrics.total; $("metricCompleted").textContent = metrics.completed; $("metricManual").textContent = metrics.manual; $("metricFailed").textContent = metrics.failed;
  $("metricTotalMeta").textContent = metrics.total ? `${state.tasks.filter(task => ACTIVE_STATUSES.has(task.status)).length} 个进行中` : "等待数据";
  $("metricCompletedMeta").textContent = `${metrics.rate}% 完成率`; $("metricManualMeta").textContent = metrics.manual ? "建议人工跟进" : "暂无待处理"; $("metricFailedMeta").textContent = metrics.failed ? "需要检查" : "链路正常";
}

function filteredTasks() {
  const query = state.query.toLowerCase();
  return state.tasks.filter(task => {
    const matchesQuery = !query || String(task.extension).includes(query) || (STATUS_LABELS[task.status] || task.status).toLowerCase().includes(query);
    const matchesStatus = state.statusFilter === "ALL" || (state.statusFilter === "ACTIVE" ? ACTIVE_STATUSES.has(task.status) : task.status === state.statusFilter);
    return matchesQuery && matchesStatus;
  });
}

function renderTasks() {
  const list = $("taskList"); list.replaceChildren(); const tasks = filteredTasks();
  if (!tasks.length) {
    const empty = document.createElement("div"); empty.className = "empty compact"; const icon = document.createElement("span"); icon.textContent = "◌"; const text = document.createElement("p"); text.textContent = state.tasks.length ? "没有匹配的任务" : "暂无外呼任务"; empty.append(icon, text); list.append(empty); return;
  }
  tasks.forEach(task => {
    const button = document.createElement("button"); button.type = "button"; button.className = `task ${task.id === state.selectedId ? "selected" : ""}`;
    const header = document.createElement("span"); header.className = "task-row"; const title = document.createElement("strong"); title.textContent = mask(task.extension);
    const badge = document.createElement("em"); badge.className = `status-badge ${statusTone(task.status)}`; badge.textContent = STATUS_LABELS[task.status] || task.status;
    const time = document.createElement("small"); time.textContent = formatTime(task.createdAt); header.append(title, badge); button.append(header, time); button.addEventListener("click", () => selectTask(task.id)); list.append(button);
  });
}

function renderCallStage(task, turns) {
  $("emptyDetail").hidden = Boolean(task); $("taskDetail").hidden = !task; if (!task) return;
  $("statusBadge").className = `status-badge ${statusTone(task.status)}`; $("statusBadge").textContent = STATUS_LABELS[task.status] || task.status;
  $("detailExtension").textContent = mask(task.extension); $("detailStatus").textContent = STATUS_LABELS[task.status] || task.status; $("detailCreated").textContent = formatTime(task.createdAt);
  $("callWave").classList.toggle("active", ACTIVE_STATUSES.has(task.status)); const latest = turns.at(-1);
  $("currentNode").textContent = latest ? (NODE_LABELS[latest.node] || latest.node) : "正在等待对话事件"; $("turnCount").textContent = `${turns.length} 轮对话`;
}

function renderConversation(turns) {
  const container = $("turns"); container.replaceChildren();
  if (!turns.length) { const empty = document.createElement("div"); empty.className = "empty compact"; const icon = document.createElement("span"); icon.textContent = "✦"; const text = document.createElement("p"); text.textContent = "对话接通后，转写和意图将显示在这里"; empty.append(icon, text); container.append(empty); return; }
  turns.forEach(turn => {
    const card = document.createElement("article"); card.className = "turn"; const heading = document.createElement("div"); heading.className = "turn-head";
    const title = document.createElement("strong"); title.className = "turn-node"; title.textContent = NODE_LABELS[turn.node] || turn.node; const attempt = document.createElement("span"); attempt.className = "intent-chip"; attempt.textContent = `第 ${Number(turn.attempt || 0) + 1} 次`;
    const transcript = document.createElement("p"); transcript.textContent = turn.transcript || "等待语音识别结果…"; const insight = document.createElement("div"); insight.className = "turn-meta";
    const intent = document.createElement("span"); intent.textContent = `意图：${turn.intent || "待判断"}`; const confidence = document.createElement("span"); confidence.textContent = turn.confidence == null ? "置信度：—" : `置信度：${Math.round(turn.confidence * 100)}%`;
    heading.append(title, attempt); insight.append(intent, confidence); card.append(heading, transcript, insight); container.append(card);
  });
}

function renderTimeline(events) {
  const timeline = $("dialogTimeline"); timeline.replaceChildren();
  if (!events.length) { const empty = document.createElement("li"); empty.className = "empty-timeline"; const icon = document.createElement("span"); icon.textContent = "○"; const text = document.createElement("p"); text.textContent = "暂无任务事件"; empty.append(icon, text); timeline.append(empty); return; }
  events.forEach(event => {
    const item = document.createElement("li"); item.className = `timeline-event ${statusTone(event.status)}`; const dot = document.createElement("span"); dot.className = "event-dot";
    const status = document.createElement("strong"); status.textContent = STATUS_LABELS[event.status] || event.status; const details = document.createElement("small"); details.textContent = `${event.message || "状态已更新"} · ${formatTime(event.occurredAt)}`;
    item.append(dot, status, details); timeline.append(item);
  });
}

async function renderDetail(id) {
  const [task, events, turns] = await Promise.all([api(`/api/tasks/${id}`), api(`/api/events/tasks/${id}`), api(`/api/tasks/${id}/turns`)]);
  if (state.selectedId !== id) return; state.detail = { task, events, turns }; renderCallStage(task, turns); renderConversation(turns); renderTimeline(events);
}
async function selectTask(id) { state.selectedId = id; renderTasks(); try { await renderDetail(id); } catch (error) { showError(error); } }
async function loadTasks() {
  const tasks = await api("/api/tasks"); state.tasks = Array.isArray(tasks) ? tasks : [];
  if (state.selectedId && !state.tasks.some(task => task.id === state.selectedId)) state.selectedId = null; if (!state.selectedId && state.tasks[0]) state.selectedId = state.tasks[0].id;
  renderMetrics(); renderTasks(); if (state.selectedId) await renderDetail(state.selectedId); else { renderCallStage(null, []); renderConversation([]); renderTimeline([]); }
}

function showToast(message, tone = "success") {
  const toast = document.createElement("div"); toast.className = `toast ${tone}`; toast.setAttribute("role", tone === "danger" ? "alert" : "status"); toast.textContent = message;
  $("toastRegion").append(toast); setTimeout(() => toast.remove(), 4200);
}

function openCallDialog() {
  $("formError").hidden = true; $("callDialog").showModal(); setTimeout(() => $("extension").focus(), 0);
}

function closeCallDialog() {
  $("callDialog").close(); $("taskForm").reset(); $("formError").hidden = true;
}

function showError(error) {
  const message = error?.message || "请求失败"; const target = $("formError");
  if (target && $("callDialog").open) { target.hidden = false; target.textContent = message; }
  showToast(message, "error"); console.error(error);
}

$("taskForm").addEventListener("submit", async event => {
  event.preventDefault(); const button = $("createButton"); button.disabled = true; $("formError").hidden = true;
  try {
    const number = $("extension").value.trim(); if (!/^1\d{10}$/.test(number)) throw new Error("请输入有效的 11 位手机号");
    if (!$("confirmed").checked) throw new Error("请先勾选确认拨打");
    const task = await api("/api/tasks", { method: "POST", body: JSON.stringify({ extension: number, promptId: "identity-question" }) }); state.selectedId = task.id;
    await api(`/api/tasks/${task.id}/start-dialog`, { method: "POST", body: JSON.stringify({ confirmed: true }) }); closeCallDialog(); showToast(`已创建 ${mask(number)} 的外呼任务`); await loadTasks();
  }
  catch (error) { showError(error); } finally { button.disabled = false; }
});

function connectEvents() {
  const stream = new EventSource("/api/events/stream");
  stream.onopen = () => { $("connectionStatus").classList.add("online"); $("connectionText").textContent = "实时连接"; state.reconnectDelay = 1000; };
  stream.addEventListener("task-event", () => loadTasks().catch(showError));
  stream.onerror = () => { stream.close(); $("connectionStatus").classList.remove("online"); $("connectionText").textContent = "连接中断"; setTimeout(connectEvents, state.reconnectDelay); state.reconnectDelay = Math.min(state.reconnectDelay * 2, 15000); };
}

$("refreshButton").addEventListener("click", () => loadTasks().catch(showError));
$("openCallDialog").addEventListener("click", openCallDialog);
$("closeCallDialog").addEventListener("click", closeCallDialog);
$("cancelCallDialog").addEventListener("click", closeCallDialog);
$("callDialog").addEventListener("click", event => { if (event.target === $("callDialog")) closeCallDialog(); });
$("callDialog").addEventListener("cancel", event => { event.preventDefault(); closeCallDialog(); });
$("taskSearch").addEventListener("input", event => { state.query = event.target.value.trim(); renderTasks(); });
$("statusFilter").addEventListener("change", event => { state.statusFilter = event.target.value; renderTasks(); });
setInterval(() => { $("liveClock").textContent = new Date().toLocaleTimeString("zh-CN", { hour12: false }); }, 1000);
loadTasks().catch(showError); connectEvents();
