import { useState, type FormEvent } from 'react';

interface LoginProps {
  onLogin: (email: string, rememberMe: boolean) => boolean;
  onSignIn: (lastName: string, firstName: string, email: string, rememberMe: boolean) => void;
  onDevLogin: () => void;
}

type Mode = 'login' | 'signin';

const ALLOWED_DOMAIN = '@sankogakuen.jp';

function generateTempPassword(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

export function Login({ onLogin, onSignIn, onDevLogin }: LoginProps) {
  const [mode, setMode] = useState<Mode>('login');

  // ログイン（メールアドレス + パスワードのみ）
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginRememberMe, setLoginRememberMe] = useState(true);
  const [loginError, setLoginError] = useState('');

  // サインイン（苗字・名前・メールアドレス + 一時パスワード）
  const [step, setStep] = useState<'email' | 'password'>('email');
  const [lastName, setLastName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tempPassword, setTempPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');

  const switchToSignIn = () => {
    setLoginError('');
    setMode('signin');
  };

  const switchToLogin = () => {
    setError('');
    setStep('email');
    setMode('login');
  };

  const handleLoginSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoginError('');

    if (!loginEmail.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
      setLoginError(`${ALLOWED_DOMAIN} のメールアドレスのみ利用できます`);
      return;
    }
    if (!loginPassword) {
      setLoginError('パスワードを入力してください');
      return;
    }

    const success = onLogin(loginEmail.trim(), loginRememberMe);
    if (!success) {
      setLoginError('アカウントが見つかりません。「またはサインイン」から登録してください');
    }
  };

  const handleSendCode = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (!lastName.trim() || !firstName.trim()) {
      setError('苗字と名前を入力してください');
      return;
    }
    if (!email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
      setError(`${ALLOWED_DOMAIN} のメールアドレスのみ利用できます`);
      return;
    }

    // メール送信基盤が無いため、モックとして一時パスワードをその場で発行する
    setTempPassword(generateTempPassword());
    setPassword('');
    setStep('password');
  };

  const handleVerify = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    if (password !== tempPassword) {
      setError('一時パスワードが正しくありません');
      return;
    }

    onSignIn(lastName.trim(), firstName.trim(), email.trim(), rememberMe);
  };

  return (
    <div className="login-page">
      <div className="login-card fade-in-up">
        <div className="login-logo">
          <div className="login-logo-icon">F</div>
          <span className="login-logo-text">FE_STS</span>
        </div>
        <p className="login-subtitle">基本情報 勉強時間共有</p>

        {mode === 'login' ? (
          <>
            <form className="login-form" onSubmit={handleLoginSubmit}>
              <label className="login-label" htmlFor="login-email-input">
                メールアドレス
              </label>
              <input
                id="login-email-input"
                className="text-input"
                type="email"
                placeholder={`例）taro${ALLOWED_DOMAIN}`}
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
              />

              <label className="login-label" htmlFor="login-password-input">
                パスワード
              </label>
              <input
                id="login-password-input"
                className="text-input"
                type="password"
                placeholder="パスワード"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
              />

              <label className="login-remember" htmlFor="login-remember-me-checkbox">
                <input
                  id="login-remember-me-checkbox"
                  type="checkbox"
                  checked={loginRememberMe}
                  onChange={(e) => setLoginRememberMe(e.target.checked)}
                />
                このブラウザを記憶して次回からログインを省略する
              </label>

              {loginError && <p className="login-error">{loginError}</p>}

              <button className="btn btn-primary login-submit" type="submit" id="login-submit-btn">
                ログイン
              </button>
            </form>

            <button
              className="login-switch-link"
              type="button"
              id="switch-to-signin-btn"
              onClick={switchToSignIn}
            >
              または サインイン
            </button>
          </>
        ) : (
          <>
            {step === 'email' ? (
              <form className="login-form" onSubmit={handleSendCode}>
                <div className="login-name-row">
                  <div className="login-name-field">
                    <label className="login-label" htmlFor="login-last-name">
                      苗字
                    </label>
                    <input
                      id="login-last-name"
                      className="text-input"
                      type="text"
                      placeholder="例）野原"
                      value={lastName}
                      onChange={(e) => setLastName(e.target.value)}
                    />
                  </div>
                  <div className="login-name-field">
                    <label className="login-label" htmlFor="login-first-name">
                      名前
                    </label>
                    <input
                      id="login-first-name"
                      className="text-input"
                      type="text"
                      placeholder="例）一誠"
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                    />
                  </div>
                </div>

                <label className="login-label" htmlFor="login-email">
                  メールアドレス
                </label>
                <input
                  id="login-email"
                  className="text-input"
                  type="email"
                  placeholder={`例）taro${ALLOWED_DOMAIN}`}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
                <p className="login-hint">{ALLOWED_DOMAIN} のアドレスのみ利用できます</p>

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
                  className="text-input"
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
                  サインイン
                </button>
                <button
                  className="btn btn-secondary login-submit"
                  type="button"
                  id="back-to-email-btn"
                  onClick={() => {
                    setStep('email');
                    setError('');
                  }}
                >
                  戻る
                </button>
              </form>
            )}

            {step === 'email' && (
              <button
                className="login-switch-link"
                type="button"
                id="switch-to-login-btn"
                onClick={switchToLogin}
              >
                ログインはこちら
              </button>
            )}
          </>
        )}

        <button
          className="dev-login-link"
          type="button"
          id="dev-login-btn"
          onClick={onDevLogin}
        >
          🛠️ 開発者ログイン
        </button>
      </div>
    </div>
  );
}
