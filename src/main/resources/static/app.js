const state = { tasks: [], selectedId: null, reconnectDelay: 1000 };
const $ = id => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
  const text = await response.text();
  if (!response.ok) throw new Error(text || `HTTP ${response.status}`);
  if (!text) return null;
  return JSON.parse(text);
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
    const title=document.createElement("strong"); title.textContent=`分机 ${task.extension} · ${task.status}`;
    const time=document.createElement("small"); time.textContent=new Date(task.createdAt).toLocaleString();
    button.append(title,time); button.onclick=()=>{state.selectedId=task.id; renderTasks(); renderDetail(task.id);}; list.append(button);
  });
}

async function renderDetail(id) {
  const task=await api(`/api/tasks/${id}`); const events=await api(`/api/events/tasks/${id}`);
  $("emptyDetail").hidden=true; $("taskDetail").hidden=false; $("statusBadge").textContent=task.status;
  $("detailExtension").textContent=task.extension; $("detailPrompt").textContent=task.promptId; $("detailCreated").textContent=new Date(task.createdAt).toLocaleString();
  const timeline=$("timeline"); timeline.replaceChildren(); events.forEach(event=>{const li=document.createElement("li"); const text=document.createElement("span"); const time=document.createElement("small"); text.textContent=`${event.status} · ${event.message}`; time.textContent=new Date(event.occurredAt).toLocaleTimeString(); li.append(text,time); timeline.append(li);});
  $("transcript").textContent=task.transcript||"等待录音处理"; $("analysis").textContent=task.analysisJson?JSON.stringify(JSON.parse(task.analysisJson),null,2):"等待模型分析";
}

function connectEvents() {
  const stream=new EventSource("/api/events/stream");
  stream.onopen=()=>{$("connectionDot").parentElement.classList.add("online"); $("connectionText").textContent="实时连接"; state.reconnectDelay=1000;};
  stream.addEventListener("task-event",()=>loadTasks().catch(showError));
  stream.onerror=()=>{stream.close(); $("connectionDot").parentElement.classList.remove("online"); $("connectionText").textContent="连接中断"; setTimeout(connectEvents,state.reconnectDelay); state.reconnectDelay=Math.min(state.reconnectDelay*2,15000);};
}

function showError(error){$("formError").hidden=false; $("formError").textContent=error.message;}
$("taskForm").addEventListener("submit",async event=>{event.preventDefault(); const button=$("createButton"); button.disabled=true; $("formError").hidden=true; try { const task=await api("/api/tasks",{method:"POST",body:JSON.stringify({extension:$("extension").value,promptId:$("promptId").value})}); state.selectedId=task.id; await api(`/api/tasks/${task.id}/start`,{method:"POST"}); await loadTasks(); } catch(error){showError(error);} finally{button.disabled=false;}});
$("refreshButton").onclick=()=>loadTasks().catch(showError);
loadTasks().catch(showError); connectEvents();
