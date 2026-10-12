-- Transaction Risk Monitoring schema + sample data (PostgreSQL / Neon).
-- Safe to re-run: tables are created only if missing, and seed data is inserted only into empty tables.

CREATE TABLE IF NOT EXISTS accounts (
    id              SERIAL PRIMARY KEY,
    account_number  VARCHAR(20)    NOT NULL UNIQUE,
    name            VARCHAR(100)   NOT NULL,
    spending_limit  NUMERIC(12, 2) NOT NULL CHECK (spending_limit > 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    id                SERIAL PRIMARY KEY,
    account_id        INT            NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    amount            NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    merchant          VARCHAR(100)   NOT NULL,
    transaction_time  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_account_time ON transactions (account_id, transaction_time);

CREATE TABLE IF NOT EXISTS risk_scores (
    id              SERIAL PRIMARY KEY,
    transaction_id  INT         NOT NULL UNIQUE REFERENCES transactions (id) ON DELETE CASCADE,
    score           INT         NOT NULL CHECK (score BETWEEN 0 AND 100),
    risk_level      VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    flagged         BOOLEAN     NOT NULL DEFAULT FALSE,
    reasons         TEXT,
    evaluated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_scores_flagged ON risk_scores (flagged) WHERE flagged;

-- ---------------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM accounts) THEN
        INSERT INTO accounts (account_number, name, spending_limit) VALUES
            ('ACC1001', 'Aarav Mehta',  50000.00),
            ('ACC1002', 'Priya Sharma', 20000.00),
            ('ACC1003', 'Rahul Verma', 100000.00),
            ('ACC1004', 'Sneha Iyer',   10000.00);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM transactions) THEN
        -- Normal spending history (gives the risk engine an average to compare against)
        INSERT INTO transactions (account_id, amount, merchant, transaction_time)
        SELECT a.id, v.amount, v.merchant, NOW() - v.age
        FROM (VALUES
            ('ACC1001',  1200.00, 'Swiggy',          INTERVAL '6 days'),
            ('ACC1001',  3500.00, 'Amazon',          INTERVAL '5 days'),
            ('ACC1001',   800.00, 'Uber',            INTERVAL '4 days'),
            ('ACC1001',  2500.00, 'BigBasket',       INTERVAL '3 days'),
            ('ACC1002',  1500.00, 'Zomato',          INTERVAL '5 days'),
            ('ACC1002',  2200.00, 'Reliance Trends', INTERVAL '4 days'),
            ('ACC1002',   900.00, 'Ola',             INTERVAL '3 days'),
            ('ACC1003',  5000.00, 'MakeMyTrip',      INTERVAL '7 days'),
            ('ACC1003',  7000.00, 'Apple Store',     INTERVAL '6 days'),
            ('ACC1003',  6000.00, 'IKEA',            INTERVAL '5 days'),
            ('ACC1004',   450.00, 'Starbucks',       INTERVAL '4 days'),
            ('ACC1004',   600.00, 'BookMyShow',      INTERVAL '3 days'),
            ('ACC1004',   300.00, 'Uber',            INTERVAL '2 days')
        ) AS v (account_number, amount, merchant, age)
        JOIN accounts a ON a.account_number = v.account_number;

        -- Scored transactions, inserted in time order so each score matches the history before it
        WITH t AS (
            INSERT INTO transactions (account_id, amount, merchant, transaction_time)
            SELECT id, 9800.00, 'Myntra', NOW() - INTERVAL '1 day' FROM accounts WHERE account_number = 'ACC1002'
            RETURNING id
        )
        INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons)
        SELECT id, 30, 'MEDIUM', FALSE, 'Over 3x the account''s average of 1533.33' FROM t;

        WITH t AS (
            INSERT INTO transactions (account_id, amount, merchant, transaction_time)
            SELECT id, 55000.00, 'Crypto Exchange', NOW() - INTERVAL '3 hours' FROM accounts WHERE account_number = 'ACC1003'
            RETURNING id
        )
        INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons)
        SELECT id, 50, 'HIGH', TRUE,
               'Over 3x the account''s average of 6000.00; At or above large-transaction threshold of 50000'
        FROM t;

        WITH t AS (
            INSERT INTO transactions (account_id, amount, merchant, transaction_time)
            SELECT id, 62000.00, 'Croma Electronics', NOW() - INTERVAL '2 hours' FROM accounts WHERE account_number = 'ACC1001'
            RETURNING id
        )
        INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons)
        SELECT id, 100, 'HIGH', TRUE,
               'Exceeds account spending limit of 50000.00; Over 3x the account''s average of 2000.00; At or above large-transaction threshold of 50000'
        FROM t;

        WITH t AS (
            INSERT INTO transactions (account_id, amount, merchant, transaction_time)
            SELECT id, 24500.00, 'Flipkart', NOW() - INTERVAL '30 minutes' FROM accounts WHERE account_number = 'ACC1002'
            RETURNING id
        )
        INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons)
        SELECT id, 80, 'HIGH', TRUE,
               'Exceeds account spending limit of 20000.00; Over 3x the account''s average of 3600.00'
        FROM t;
    END IF;
END $$;
