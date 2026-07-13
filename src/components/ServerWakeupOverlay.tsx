interface ServerWakeupOverlayProps {
  elapsedSeconds: number;
}

export function ServerWakeupOverlay({ elapsedSeconds }: ServerWakeupOverlayProps) {
  return (
    <div className="server-wakeup-overlay">
      <div className="server-wakeup-card">
        <p className="server-wakeup-message">
          サーバーを起動しています。無料サーバーのため、しばらく使われていないと起動に最大1分ほどかかります ☕
        </p>
        <p className="server-wakeup-elapsed">{elapsedSeconds}秒経過</p>
      </div>
    </div>
  );
}
