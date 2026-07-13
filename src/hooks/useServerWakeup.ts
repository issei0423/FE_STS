import { useEffect, useState } from 'react';
import { API_BASE_URL } from '../api/client';

export type ServerWakeupStatus = 'checking' | 'waking' | 'ready';

const PING_TIMEOUT_MS = 3000;
const POLL_INTERVAL_MS = 3000;

async function pingHealth(signal: AbortSignal): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/actuator/health`, { signal });
    return res.ok;
  } catch {
    return false;
  }
}

/**
 * Renderの無料プランはスリープ復帰に最大1分程度かかる。/actuator/healthへの
 * 初回pingがタイムアウト・失敗した場合は起動待ちとみなし、成功するまで
 * ポーリングする(起動済みの通常アクセスでは何も表示しない)。
 */
export function useServerWakeup(): { status: ServerWakeupStatus; elapsedSeconds: number } {
  const [status, setStatus] = useState<ServerWakeupStatus>('checking');
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let pollTimer: number | undefined;
    let elapsedTimer: number | undefined;
    const controllers = new Set<AbortController>();

    const check = async (): Promise<boolean> => {
      const controller = new AbortController();
      controllers.add(controller);
      const timeoutId = window.setTimeout(() => controller.abort(), PING_TIMEOUT_MS);
      const ok = await pingHealth(controller.signal);
      window.clearTimeout(timeoutId);
      controllers.delete(controller);
      return ok;
    };

    const startPolling = () => {
      if (cancelled) return;
      setStatus('waking');
      elapsedTimer = window.setInterval(() => {
        if (!cancelled) setElapsedSeconds((s) => s + 1);
      }, 1000);

      const poll = async () => {
        const ok = await check();
        if (cancelled) return;
        if (ok) {
          setStatus('ready');
          if (pollTimer !== undefined) window.clearInterval(pollTimer);
          if (elapsedTimer !== undefined) window.clearInterval(elapsedTimer);
        }
      };
      pollTimer = window.setInterval(poll, POLL_INTERVAL_MS);
    };

    (async () => {
      const ok = await check();
      if (cancelled) return;
      if (ok) {
        setStatus('ready');
      } else {
        startPolling();
      }
    })();

    return () => {
      cancelled = true;
      if (pollTimer !== undefined) window.clearInterval(pollTimer);
      if (elapsedTimer !== undefined) window.clearInterval(elapsedTimer);
      controllers.forEach((c) => c.abort());
    };
  }, []);

  return { status, elapsedSeconds };
}
