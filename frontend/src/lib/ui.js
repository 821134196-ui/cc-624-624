import { writable } from 'svelte/store';

export const toast = writable(null);
let toastTimer = null;

export function showToast(msg, isError = false) {
  toast.set({ msg, isError });
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.set(null), 3200);
}

export const REASONS = [
  { key: 'FAILURE', label: '故障' },
  { key: 'WAIT_MATERIAL', label: '待料' },
  { key: 'CHANGEOVER', label: '换线' }
];

export function reasonLabel(key) {
  return REASONS.find((r) => r.key === key)?.label || key;
}

export const STATUS_LABEL = {
  REGISTERED: '待分段',
  SEGMENTED: '待复核',
  REVIEWED: '已复核'
};
