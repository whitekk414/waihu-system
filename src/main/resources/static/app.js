const state = { tasks: [], selectedId: null, reconnectDelay: 1000 };
const $ = id => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
  const text = await response.text();
  const contentType = response.headers.get("content-type") || "";
  let body = null;
  if (!text.trim() && response.ok) return null;
  if (!text.trim()) throw new Error(`HTTP ${response.status}`);
  if (contentType.includes("application/json")) {
    try { body = JSON.parse(text); } catch (_) { body = null; }
  }
  if (!response.ok) {
    const message = body?.message || body?.detail || text || `HTTP ${response.status}`;
    throw new Error(message);
  }
  return body ?? text;
}

async function loadTasks() {
  state.tasks = await api("/api/tasks");
  if (!state.selectedId && state.tasks[0]) state.selectedId = state.tasks[0].id;
  renderTasks();
  if (state.selectedId) await renderDetail(state.selectedId);
}

function renderTasks() {
  const list = $("taskList"); list.replaceChildren();
  if (!state.tasks.length) { const p=document.createElement("p"); p.className="empty"; p.textContent="还没有测试任务"; list.append(p); return; }
  state.tasks.forEach(task => {
    const button=document.createElement("button"); button.className=`task${task.id===state.selectedId?" selected":""}`;
    const title=document.createElement("strong"); title.textContent=`${mask(task.extension)} · ${task.status}`;
    const time=document.createElement("small"); time.textContent=new Date(task.createdAt).toLocaleString();
    button.append(title,time); button.onclick=()=>{state.selectedId=task.id; renderTasks(); renderDetail(task.id);}; list.append(button);
  });
}

async function renderDetail(id) {
  const [task, events, turns] = await Promise.all([
    api(`/api/tasks/${id}`), api(`/api/events/tasks/${id}`), api(`/api/tasks/${id}/turns`)
  ]);
  $("emptyDetail").hidden=true; $("taskDetail").hidden=false; $("statusBadge").textContent=task.status;
  $("detailExtension").textContent=mask(task.extension); $("detailStatus").textContent=task.status;
  $("detailCreated").textContent=new Date(task.createdAt).toLocaleString();
  const timeline=$("timeline"); timeline.replaceChildren();
  events.forEach(event=>{const li=document.createElement("li"); li.textContent=`${event.status} · ${event.message}`; timeline.append(li);});
  const container=$("turns"); container.replaceChildren();
  if (!turns.length) { const p=document.createElement("p"); p.className="empty"; p.textContent="等待回答"; container.append(p); }
  turns.forEach(turn=>{const card=document.createElement("article"); card.className="turn";
    card.textContent=`${turn.node} / 第 ${turn.attempt+1} 次\n转写：${turn.transcript||"等待识别"}\n意图：${turn.intent||"等待判断"} ${turn.confidence??""}`; container.append(card);});
}

function mask(number){ return /^1\d{10}$/.test(number) ? `${number.slice(0,3)}****${number.slice(-4)}` : number; }
function showError(error){$("formError").hidden=false; $("formError").textContent=error.message;}

$("taskForm").addEventListener("submit",async event=>{
  event.preventDefault(); const button=$("createButton"); button.disabled=true; $("formError").hidden=true;
  try {
    const number=$("extension").value.trim();
    if (!$("confirmed").checked) throw new Error("请先勾选确认拨打");
    const task=await api("/api/tasks",{method:"POST",body:JSON.stringify({extension:number,promptId:"identity-question"})});
    state.selectedId=task.id;
    await api(`/api/tasks/${task.id}/start-dialog`,{method:"POST",body:JSON.stringify({confirmed:true})});
    await loadTasks();
  } catch(error){showError(error);} finally{button.disabled=false;}
});

function connectEvents() {
  const stream=new EventSource("/api/events/stream");
  stream.onopen=()=>{$("connectionDot").parentElement.classList.add("online"); $("connectionText").textContent="实时连接"; state.reconnectDelay=1000;};
  stream.addEventListener("task-event",()=>loadTasks().catch(showError));
  stream.onerror=()=>{stream.close(); $("connectionDot").parentElement.classList.remove("online"); $("connectionText").textContent="连接中断"; setTimeout(connectEvents,state.reconnectDelay); state.reconnectDelay=Math.min(state.reconnectDelay*2,15000);};
}

$("refreshButton").onclick=()=>loadTasks().catch(showError);
loadTasks().catch(showError); connectEvents();
