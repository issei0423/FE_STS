import { useState, type FormEvent } from 'react';

interface LoginProps {
  onLoginSuccess: (name: string, rememberMe: boolean) => void;
}

const ALLOWED_DOMAIN = '@sankogakuen.jp';

function generateTempPassword(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

export function Login({ onLoginSuccess }: LoginProps) {
  const [step, setStep] = useState<'email' | 'password'>('email');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tempPassword, setTempPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');

  const handleSendCode = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (!name.trim()) {
      setError('本名を入力してください');
      return;
    }
    if (!email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
      setError(`${ALLOWED_DOMAIN} のメールアドレスのみ利用できます`);
      return;
    }

    // メール送信基盤が無いため、モックとして一時パスワードをその場で発行する
    setTempPassword(generateTempPassword());
    setStep('password');
  };

  const handleVerify = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (password !== tempPassword) {
      setError('一時パスワードが正しくありません');
      return;
    }

    onLoginSuccess(name.trim(), rememberMe);
  };

  return (
    <div className="login-page">
      <div className="login-card fade-in-up">
        <div className="login-logo">
          <div className="login-logo-icon">F</div>
          <span className="login-logo-text">FE_STS</span>
        </div>
        <p className="login-subtitle">基本情報 勉強時間共有</p>

        {step === 'email' ? (
          <form className="login-form" onSubmit={handleSendCode}>
            <label className="login-label" htmlFor="login-name">
              氏名（本名）
            </label>
            <input
              id="login-name"
              className="login-input"
              type="text"
              placeholder="例）野原 一誠"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />

            <label className="login-label" htmlFor="login-email">
              メールアドレス
            </label>
            <input
              id="login-email"
              className="login-input"
              type="email"
              placeholder={`例）taro${ALLOWED_DOMAIN}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <p className="login-hint">{ALLOWED_DOMAIN} のアドレスのみログインできます</p>

            {error && <p className="login-error">{error}</p>}

            <button className="btn btn-primary login-submit" type="submit" id="send-code-btn">
              一時パスワードを送信
            </button>
          </form>
        ) : (
          <form className="login-form" onSubmit={handleVerify}>
            <p className="login-hint">{email} 宛に一時パスワードを送信しました</p>
            {/* デモ用: 実際のメール送信基盤が無いため画面上にも表示する */}
            <p className="login-mock-code">開発用コード: {tempPassword}</p>

            <label className="login-label" htmlFor="login-password">
              一時パスワード
            </label>
            <input
              id="login-password"
              className="login-input"
              type="text"
              inputMode="numeric"
              placeholder="6桁のコード"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoFocus
            />

            <label className="login-remember" htmlFor="remember-me-checkbox">
              <input
                id="remember-me-checkbox"
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
              />
              このブラウザを記憶して次回からログインを省略する
            </label>

            {error && <p className="login-error">{error}</p>}

            <button className="btn btn-primary login-submit" type="submit" id="verify-code-btn">
              ログイン
            </button>
            <button
              className="btn btn-secondary login-submit"
              type="button"
              id="back-to-email-btn"
              onClick={() => setStep('email')}
            >
              戻る
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
