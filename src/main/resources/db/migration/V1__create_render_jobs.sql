
CREATE TABLE render_jobs (
    id UUID PRIMARY KEY,

    preset VARCHAR(20) NOT NULL
        CHECK (preset IN (
            'VERTICAL',
            'SQUARE',
            'LANDSCAPE'
        )),

    status VARCHAR(20) NOT NULL
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED'
        )),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    error TEXT
);

CREATE INDEX idx_render_jobs_status_created
ON render_jobs(status, created_at);
