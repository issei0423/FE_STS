import { useState, type FormEvent } from 'react';
import { api, ApiError, type ApiUser } from '../api/client';

interface LoginProps {
  onAuthenticated: (accessToken: string, user: ApiUser, rememberMe: boolean) => void;
  verifyError?: string;
}

type Mode = 'login' | 'signin';

const ALLOWED_DOMAIN = '@sankogakuen.jp';

export function Login({ onAuthenticated, verifyError }: LoginProps) {
  const [mode, setMode] = useState<Mode>('login');
  const [submitting, setSubmitting] = useState(false);

  // ログイン（メールアドレス + パスワードのみ）
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginRememberMe, setLoginRememberMe] = useState(true);
  const [loginError, setLoginError] = useState('');

  // サインイン（苗字・名前・メールアドレス・パスワード → 確認メール）
  const [lastName, setLastName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');
  const [signupSent, setSignupSent] = useState(false);
  const [devVerificationUrl, setDevVerificationUrl] = useState<string | null>(null);

  const switchToSignIn = () => {
    setLoginError('');
    setMode('signin');
  };

  const switchToLogin = () => {
    setError('');
    setSignupSent(false);
    setDevVerificationUrl(null);
    setMode('login');
  };

  const handleLoginSubmit = async (e: FormEvent<HTMLFormElement>) => {
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

    setSubmitting(true);
    try {
      const res = await api.login(loginEmail.trim(), loginPassword);
      onAuthenticated(res.accessToken, res.user, loginRememberMe);
    } catch (err) {
      setLoginError(err instanceof ApiError ? err.message : 'ログインに失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSignupSubmit = async (e: FormEvent<HTMLFormElement>) => {
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
    if (password.length < 8) {
      setError('パスワードは8文字以上で入力してください');
      return;
    }

    setSubmitting(true);
    try {
      const res = await api.signup(lastName.trim(), firstName.trim(), email.trim(), password);
      setSignupSent(true);
      setDevVerificationUrl(res.verificationUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'サインインに失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDevVerify = async () => {
    if (!devVerificationUrl) return;
    const token = new URL(devVerificationUrl).searchParams.get('token');
    if (!token) return;

    setSubmitting(true);
    try {
      const res = await api.verify(token);
      onAuthenticated(res.accessToken, res.user, rememberMe);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '確認に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDevLogin = async () => {
    setSubmitting(true);
    try {
      const res = await api.devLogin();
      onAuthenticated(res.accessToken, res.user, false);
    } catch (err) {
      setLoginError(err instanceof ApiError ? err.message : '開発者ログインに失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card fade-in-up">
        <div className="login-logo">
          <div className="login-logo-icon">F</div>
          <span className="login-logo-text">FE_STS</span>
        </div>
        <p className="login-subtitle">基本情報 勉強時間共有</p>

        {verifyError && <p className="login-error">{verifyError}</p>}

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

              <button
                className="btn btn-primary login-submit"
                type="submit"
                id="login-submit-btn"
                disabled={submitting}
              >
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
            {!signupSent ? (
              <form className="login-form" onSubmit={handleSignupSubmit}>
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

                <label className="login-label" htmlFor="login-new-password">
                  パスワード
                </label>
                <input
                  id="login-new-password"
                  className="text-input"
                  type="password"
                  placeholder="8文字以上"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />

                <label className="login-remember" htmlFor="signup-remember-me-checkbox">
                  <input
                    id="signup-remember-me-checkbox"
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                  />
                  このブラウザを記憶して次回からログインを省略する
                </label>

                {error && <p className="login-error">{error}</p>}

                <button
                  className="btn btn-primary login-submit"
                  type="submit"
                  id="send-code-btn"
                  disabled={submitting}
                >
                  確認メールを送信
                </button>
              </form>
            ) : (
              <div className="login-form">
                <p className="login-hint">
                  {email} 宛に確認メールを送信しました。メール内のリンクをクリックして登録を完了してください。
                </p>

                {devVerificationUrl && (
                  <>
                    <p className="login-mock-code">
                      開発用: 実際のメール送信基盤が無い環境のため確認リンクをここに表示しています
                    </p>
                    <button
                      className="btn btn-primary login-submit"
                      type="button"
                      id="dev-verify-btn"
                      onClick={handleDevVerify}
                      disabled={submitting}
                    >
                      開発用: 確認リンクを開く
                    </button>
                  </>
                )}

                <button
                  className="btn btn-secondary login-submit"
                  type="button"
                  id="back-to-email-btn"
                  onClick={() => {
                    setSignupSent(false);
                    setDevVerificationUrl(null);
                  }}
                >
                  戻る
                </button>
              </div>
            )}

            {!signupSent && (
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

        {/* バックエンド側もlocal/testプロファイル限定のため、本番ビルドでは表示しない */}
        {import.meta.env.DEV && (
          <button
            className="dev-login-link"
            type="button"
            id="dev-login-btn"
            onClick={handleDevLogin}
            disabled={submitting}
          >
            🛠️ 開発者ログイン
          </button>
        )}
      </div>
    </div>
  );
}
