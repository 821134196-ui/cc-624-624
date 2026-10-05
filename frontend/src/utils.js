/** 时间工具：业务统一按车间时区 Asia/Shanghai 展示与录入 */
const TZ = 'Asia/Shanghai';

const inputFmt = new Intl.DateTimeFormat('sv-SE', {
  timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hour12: false
});

const displayFmt = new Intl.DateTimeFormat('zh-CN', {
  timeZone: TZ, month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hour12: false
});

/** Instant(ISO) → datetime-local 输入框值（车间时区墙钟） */
export function toInputValue(iso) {
  if (!iso) return '';
  return inputFmt.format(new Date(iso)).replace(' ', 'T');
}

/** datetime-local 输入框值（车间时区墙钟）→ Instant(ISO)。上海恒为 UTC+8 */
export function fromInputValue(v) {
  if (!v) return null;
  const withSec = v.length === 16 ? `${v}:00` : v;
  return new Date(`${withSec}+08:00`).toISOString();
}

/** Instant(ISO) → 展示字符串 */
export function fmtTime(iso) {
  if (!iso) return '—';
  return displayFmt.format(new Date(iso));
}

/** 秒 → “1小时23分” */
export function fmtDuration(seconds) {
  if (seconds == null) return '—';
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 0) return `${h}小时${m > 0 ? m + '分' : ''}`;
  if (m > 0) return `${m}分${s % 60 > 0 ? (s % 60) + '秒' : ''}`;
  return `${s}秒`;
}

export const REASONS = [
  { code: 'FAULT', label: '故障', color: '#e5484d' },
  { code: 'WAIT_MATERIAL', label: '待料', color: '#f5a524' },
  { code: 'CHANGEOVER', label: '换线', color: '#3e8fe0' },
  { code: 'OTHER', label: '其他', color: '#8b8d98' }
];

export function reasonColor(code) {
  return (REASONS.find(r => r.code === code) || {}).color || '#8b8d98';
}

export function reasonLabel(code) {
  return (REASONS.find(r => r.code === code) || {}).label || code;
}

export const STATUS_LABELS = {
  OPEN: '登记中',
  PENDING_REVIEW: '待复核',
  REVIEWED: '已复核'
};

/** 当前车间时区日期（YYYY-MM-DD） */
export function todayInWorkshop() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit'
  }).format(new Date());
}
