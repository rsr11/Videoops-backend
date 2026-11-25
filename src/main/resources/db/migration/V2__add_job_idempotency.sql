
ALTER TABLE render_jobs
ADD COLUMN idempotency_key VARCHAR(64),
ADD COLUMN request_fingerprint VARCHAR(64);

-- Prevent two jobs using the same key
CREATE UNIQUE INDEX uq_render_jobs_idempotency_key
ON render_jobs (idempotency_key)
WHERE idempotency_key IS NOT NULL;

-- Ensure both values exist together
ALTER TABLE render_jobs
ADD CONSTRAINT chk_job_idempotency_pair
CHECK (
    (idempotency_key IS NULL
     AND request_fingerprint IS NULL)
    OR
    (idempotency_key IS NOT NULL
     AND request_fingerprint IS NOT NULL)
);
