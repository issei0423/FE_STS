-- issue #15: 開放セッション(ended_at IS NULL)の走査を高速化する複合インデックス。
-- StudySessionCleanupScheduler.closeStaleSessions() / UserRosterService.listRoster()
-- (findActiveSince, issue #13) の双方がこの2カラムで絞り込むため対象にした。
-- MySQLは部分インデックス(WHERE句付き)をサポートしないため通常の複合インデックスとする。
CREATE INDEX idx_ss_ended_heartbeat ON study_sessions(ended_at, last_heartbeat_at);
