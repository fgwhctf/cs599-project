<template>
  <div class="chat-page">
    <div ref="messagesEl" class="messages">
      <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-row', msg.role]"
      >
        <div v-if="msg.role === 'assistant'" class="avatar">AI</div>

        <div class="bubble">
          <pre v-if="msg.type === 'text'" class="text-message">{{ msg.content }}</pre>

          <div v-else-if="msg.type === 'rewrite'" class="rewrite-message">
            <span>理解为：</span>{{ msg.content }}
          </div>

          <pre v-else-if="msg.type === 'sql'" class="sql-block"><code>{{ msg.content }}</code></pre>

          <div v-else-if="msg.type === 'steps'" class="steps">
            <div v-for="(step, sIdx) in msg.steps" :key="sIdx" class="step">
              <span class="dot" :class="step.status"></span>
              <span>{{ step.text }}</span>
            </div>
          </div>

          <div v-else-if="msg.type === 'table'" class="table-wrap">
            <table class="result-table">
              <thead>
              <tr>
                <th v-for="col in msg.columns" :key="col">
                  {{ col }}
                </th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="(row, rIdx) in msg.rows" :key="rIdx">
                <td v-for="col in msg.columns" :key="col">
                  {{ row[col] }}
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <div v-else-if="msg.type === 'error'" class="error-text">
            {{ msg.content }}
          </div>
        </div>

        <div v-if="msg.role === 'user'" class="avatar">我</div>
      </div>
      <div class="messages-bottom-spacer"></div>
    </div>

    <div class="input-wrapper">
      <div class="input-box">
        <input
            v-model="question"
            @keyup.enter="sendQuestion"
            placeholder="请输入你的问题..."
        />
        <button class="reset-button" @click="resetConversation" :disabled="loading">
          重置
        </button>
        <button @click="sendQuestion" :disabled="loading">
          {{ loading ? "执行中..." : "发送" }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {nextTick, ref, watch} from "vue";

const API_URL = "/api/query";
const SESSION_KEY = "data-agent-session-id";
const MAX_SAVED_MESSAGES = 40;

const question = ref("");
const loading = ref(false);
const messagesEl = ref(null);
const sessionId = ref(getSessionId());
const MESSAGES_KEY = ref(messageKey(sessionId.value));
const messages = ref(loadMessages());

watch(
    messages,
    (value) => {
      localStorage.setItem(MESSAGES_KEY.value, JSON.stringify(value.slice(-MAX_SAVED_MESSAGES)));
    },
    {deep: true}
);

function getSessionId() {
  let value = localStorage.getItem(SESSION_KEY);
  if (!value) {
    value = crypto.randomUUID();
    localStorage.setItem(SESSION_KEY, value);
  }
  return value;
}

function loadMessages() {
  try {
    const saved = JSON.parse(localStorage.getItem(MESSAGES_KEY.value) || "[]");
    return Array.isArray(saved) ? saved : [];
  } catch {
    return [];
  }
}

function messageKey(value) {
  return `data-agent-messages:${value}`;
}

async function resetConversation() {
  const oldSessionId = sessionId.value;
  localStorage.removeItem(messageKey(oldSessionId));

  try {
    await fetch(`/api/conversations/${encodeURIComponent(oldSessionId)}`, {
      method: "DELETE",
    });
  } catch {
    // The UI can still start a fresh local session if the backend is unavailable.
  }

  sessionId.value = crypto.randomUUID();
  localStorage.setItem(SESSION_KEY, sessionId.value);
  MESSAGES_KEY.value = messageKey(sessionId.value);
  messages.value = [];
}

function scrollToBottom() {
  const el = messagesEl.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

function splitSseEvents(buffer) {
  const parts = buffer.split(/\r?\n\r?\n/);
  return {
    events: parts.slice(0, -1),
    rest: parts.at(-1) ?? "",
  };
}

function parseSseData(evt) {
  const dataLines = evt
      .split(/\r?\n/)
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.replace(/^data:\s*/, ""));

  if (!dataLines.length) return null;

  try {
    return JSON.parse(dataLines.join("\n"));
  } catch {
    return null;
  }
}

function appendResult(payload) {
  const result = payload?.data;
  const rows = Array.isArray(result) ? result : result?.rows;
  const sql = Array.isArray(result) ? null : result?.sql;

  if (sql) {
    messages.value.push({
      role: "assistant",
      type: "sql",
      content: sql,
    });
  }

  if (Array.isArray(rows) && rows.length > 0) {
    messages.value.push({
      role: "assistant",
      type: "table",
      columns: Object.keys(rows[0] || {}),
      rows,
    });
    return;
  }

  messages.value.push({
    role: "assistant",
    type: "text",
    content: "查询完成，没有返回数据。",
  });
}

async function sendQuestion() {
  if (!question.value || loading.value) return;

  const q = question.value;
  question.value = "";
  loading.value = true;

  messages.value.push({role: "user", type: "text", content: q});

  const stepIndex =
      messages.value.push({
        role: "assistant",
        type: "steps",
        steps: [],
      }) - 1;

  await nextTick();
  scrollToBottom();

  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({sessionId: sessionId.value, query: q}),
    });

    if (!response.ok) throw new Error(`请求失败：${response.status}`);
    if (!response.body) throw new Error("服务端未返回流");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
      const {value, done} = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, {stream: true});
      const parsed = splitSseEvents(buffer);
      buffer = parsed.rest;

      for (const evt of parsed.events) {
        const data = parseSseData(evt);
        if (!data) continue;

        const steps = messages.value[stepIndex]?.steps ?? [];

        if (data.type === "rewrite") {
          messages.value.push({
            role: "assistant",
            type: "rewrite",
            content: data.query,
          });
        } else if (data.type === "progress") {
          let step = steps.find((s) => s.text === data.step);

          if (!step) {
            step = {
              text: data.step,
              status: data.status,
            };
            steps.push(step);
          } else {
            step.status = data.status;
          }
        } else if (data.type === "result") {
          removeStepMessage(stepIndex);
          appendResult(data);
        } else if (data.type === "error") {
          removeStepMessage(stepIndex);
          messages.value.push({
            role: "assistant",
            type: "error",
            content: data.message || "发生错误",
          });
        }

        await nextTick();
        scrollToBottom();
      }
    }
  } catch (e) {
    removeStepMessage(stepIndex);
    messages.value.push({
      role: "assistant",
      type: "error",
      content: e?.message || "请求失败",
    });
  } finally {
    loading.value = false;
    await nextTick();
    scrollToBottom();
  }
}

function removeStepMessage(stepIndex) {
  if (messages.value[stepIndex]?.type === "steps") {
    messages.value.splice(stepIndex, 1);
  }
}
</script>

<style scoped>
:global(html),
:global(body) {
  height: 100%;
  margin: 0;
}

:global(body) {
  display: block !important;
  place-items: unset !important;
}

:global(#app) {
  height: 100%;
  max-width: none !important;
  margin: 0 !important;
  padding: 0 !important;
}

.chat-page {
  height: 100%;
  overflow: hidden;
  background: #fff;
}

.messages {
  height: 100%;
  overflow-y: auto;
  padding: 20px 20% 160px;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-row.user {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 10px;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
}

.bubble {
  max-width: min(820px, 72%);
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f5f5;
}

.message-row.user .bubble {
  background: #e6f4ff;
}

.text-message {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font: inherit;
}

.rewrite-message {
  color: #4b5563;
  line-height: 1.6;
  white-space: pre-wrap;
}

.rewrite-message span {
  color: #2563eb;
  font-weight: 700;
}

.sql-block {
  margin: 0;
  max-width: 100%;
  overflow-x: auto;
  padding: 12px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre;
}

.sql-block code {
  font: inherit;
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.running {
  background: #f1c40f;
}

.dot.success {
  background: #2ecc71;
}

.dot.error {
  background: #e74c3c;
}

.table-wrap {
  max-width: 100%;
  overflow-x: auto;
}

.result-table {
  width: max-content;
  min-width: 100%;
  table-layout: auto;
  border-collapse: collapse;
}

.result-table th,
.result-table td {
  border: 1px solid #ddd;
  padding: 6px 12px;
  white-space: nowrap;
  font-size: 13px;
  text-align: left;
}

.result-table th {
  background: #fafafa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.error-text {
  color: #e74c3c;
  font-weight: 600;
}

.input-wrapper {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 24px;
  display: flex;
  justify-content: center;
  padding: 0 16px;
  pointer-events: none;
}

.input-box {
  pointer-events: auto;
  width: 100%;
  max-width: 720px;
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
}

.input-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
}

.input-box button {
  padding: 8px 18px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  cursor: pointer;
}

.input-box .reset-button {
  background: #f3f4f6;
  color: #374151;
}

.input-box button:disabled {
  opacity: 0.5;
}

.messages-bottom-spacer {
  height: 200px;
}
</style>
