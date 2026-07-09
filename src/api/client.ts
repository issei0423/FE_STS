export const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

export type Role = 'STUDENT' | 'DEVELOPER';

export interface ApiUser {
  id: number;
  lastName: string;
  firstName: string;
  email: string;
  role: Role;
  iconUrl: string | null;
}

export interface LoginResponse {
  accessToken: string;
  user: ApiUser;
}

export interface SignupResponse {
  message: string;
  /** ローカル/開発環境でのみ埋まる(本番のBrevoメール送信の代わり)。 */
  verificationUrl: string | null;
}

export interface StudySessionResponse {
  id: number | null;
  startedAt: string | null;
  endedAt: string | null;
  durationSec: number | null;
  running: boolean;
  todayTotalSec: number;
}

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

interface RequestOptions {
  method?: string;
  token?: string;
  body?: unknown;
  isFormData?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }
  if (options.body && !options.isFormData) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.isFormData
      ? (options.body as FormData)
      : options.body
        ? JSON.stringify(options.body)
        : undefined,
  });

  if (!response.ok) {
    let code = 'UNKNOWN_ERROR';
    let message = `リクエストに失敗しました (${response.status})`;
    try {
      const data = await response.json();
      code = data.code ?? code;
      message = data.message ?? message;
    } catch {
      // レスポンスボディが無い/JSONでない場合はデフォルトメッセージのまま
    }
    throw new ApiError(response.status, code, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  signup: (lastName: string, firstName: string, email: string, password: string) =>
    request<SignupResponse>('/api/auth/signup', {
      method: 'POST',
      body: { lastName, firstName, email, tempPassword: password },
    }),

  verify: (token: string) =>
    request<LoginResponse>(`/api/auth/verify?token=${encodeURIComponent(token)}`),

  login: (email: string, password: string) =>
    request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    }),

  devLogin: () => request<LoginResponse>('/api/auth/dev-login', { method: 'POST' }),

  me: (token: string) => request<ApiUser>('/api/users/me', { token }),

  uploadIcon: (token: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<{ message: string }>('/api/users/me/icon', {
      method: 'POST',
      token,
      body: formData,
      isFormData: true,
    });
  },

  iconUrl: (userId: number) => `${API_BASE_URL}/api/users/${userId}/icon`,

  startSession: (token: string) =>
    request<StudySessionResponse>('/api/study-sessions/start', { method: 'POST', token }),

  stopSession: (token: string) =>
    request<StudySessionResponse>('/api/study-sessions/stop', { method: 'POST', token }),

  todaySession: (token: string) =>
    request<StudySessionResponse>('/api/study-sessions/today', { token }),
};
