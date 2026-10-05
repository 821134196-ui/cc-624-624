// 时间处理:后端存 UTC Instant(ISO 字符串),前端按浏览器本地时区(车间时区)
// 用 <input type="datetime-local"> 编辑,需要 ISO <-> 本地无时区字符串互转。

export function toLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
         `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function localInputToIso(s) {
  if (!s) return null;
  // datetime-local: "YYYY-MM-DDTHH:mm:ss"(空格容错)
  const d = new Date(s.replace(' ', 'T'));
  return d.toISOString();
}

export function fmt(iso, withSec = true) {
  if (!iso) return '—';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  const base = `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return withSec ? `${base}:${pad(d.getSeconds())}` : base;
}

export function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function duration(sec) {
  if (sec == null) return '—';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const parts = [];
  if (h) parts.push(`${h}小时`);
  if (m) parts.push(`${m}分`);
  if (s || parts.length === 0) parts.push(`${s}秒`);
  return parts.join('');
}

export function minutes(sec) {
  return (sec / 60).toFixed(1);
}
