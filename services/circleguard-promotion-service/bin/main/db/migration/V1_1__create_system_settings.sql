CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    unconfirmed_fencing_enabled BOOLEAN NOT NULL DEFAULT true,
    auto_threshold_seconds BIGINT NOT NULL DEFAULT 300
);

INSERT INTO system_settings (unconfirmed_fencing_enabled, auto_threshold_seconds)
SELECT true, 300
WHERE NOT EXISTS (SELECT 1 FROM system_settings);
