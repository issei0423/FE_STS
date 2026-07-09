export interface User {
  id: string;
  name: string;
  initials: string;
  status: 'online' | 'studying' | 'offline';
  currentSessionMinutes: number;
  totalStudyHours: number;
  subject?: string;
}

export type RankingPeriod = 'today' | 'week' | 'total';
