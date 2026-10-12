-- Adds 46 more accounts (ACC1005-ACC1050) with ~30 days of realistic spending history and a batch of
-- recently scored anomalies. Run once AFTER schema.sql. Safe to re-run: does nothing if ACC1005 exists.
-- Anomaly scores are computed with the same rules and wording as RiskService (velocity rule not seeded).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM accounts WHERE account_number = 'ACC1005') THEN
        RAISE NOTICE 'Extra seed data already present, skipping';
        RETURN;
    END IF;

    PERFORM setseed(0.42);

    -- 46 accounts with unique names and spending limits from 5,000 to 200,000
    INSERT INTO accounts (account_number, name, spending_limit)
    SELECT 'ACC' || (1004 + g),
           first_names[1 + (g * 7) % array_length(first_names, 1)] || ' ' ||
           last_names[1 + (g * 11) % array_length(last_names, 1)],
           (ARRAY[5000, 10000, 15000, 25000, 40000, 50000, 75000, 100000, 150000, 200000])[1 + (g * 3) % 10]
    FROM generate_series(1, 46) AS g,
         (SELECT ARRAY['Aditi', 'Arjun', 'Kavya', 'Rohan', 'Ishita', 'Vikram', 'Meera', 'Kabir', 'Ananya',
                       'Dev', 'Nisha', 'Siddharth', 'Pooja', 'Karan', 'Riya', 'Aman', 'Tanvi', 'Nikhil',
                       'Shreya', 'Varun', 'Diya', 'Harsh', 'Neha'] AS first_names,
                 ARRAY['Kapoor', 'Nair', 'Reddy', 'Gupta', 'Joshi', 'Menon', 'Bose', 'Malhotra', 'Pillai',
                       'Chopra', 'Desai', 'Kulkarni', 'Banerjee', 'Rao', 'Saxena', 'Agarwal', 'Thomas',
                       'Bhatt', 'Singh'] AS last_names) AS names;

    -- 10-30 normal transactions per new account, spread over the last 2-30 days.
    -- Each account has a "typical" spend of 3-12% of its limit; amounts vary 40-160% around it.
    INSERT INTO transactions (account_id, amount, merchant, transaction_time)
    SELECT a.id,
           ROUND(a.spending_limit * (0.03 + (a.id * 37 % 10) / 100.0) * (0.4 + random()::numeric * 1.2), 2),
           (ARRAY['Amazon', 'Flipkart', 'Swiggy', 'Zomato', 'Uber', 'Ola', 'BigBasket', 'Myntra', 'Reliance Digital',
                  'BookMyShow', 'IRCTC', 'MakeMyTrip', 'Starbucks', 'DMart', 'Nykaa', 'Croma', 'Apollo Pharmacy',
                  'Airtel', 'Jio', 'Decathlon', 'IKEA', 'Zepto', 'Blinkit', 'Shell', 'Indian Oil'])[1 + floor(random() * 25)::int],
           NOW() - INTERVAL '2 days' - random() * INTERVAL '28 days'
    FROM accounts a
    CROSS JOIN LATERAL generate_series(1, 10 + (a.id * 13) % 21) AS s
    WHERE a.account_number >= 'ACC1005';

    -- One recent anomaly for every third new account, scored against the history above
    WITH stats AS (
        SELECT a.id AS account_id, a.spending_limit AS spending_limit,
               COUNT(t.id) AS history_count, AVG(t.amount) AS average_amount
        FROM accounts a
        JOIN transactions t ON t.account_id = a.id
        WHERE a.account_number >= 'ACC1005' AND a.id % 3 = 0
        GROUP BY a.id, a.spending_limit
    ),
    candidates AS (
        SELECT s.*,
               ROUND(CASE s.account_id % 9
                         WHEN 0 THEN s.spending_limit * 1.3    -- blows through the limit
                         WHEN 3 THEN s.average_amount * 4      -- unusual for this account, within limit
                         ELSE s.spending_limit * 0.9           -- near the limit, far above normal
                     END, 2) AS amount,
               (ARRAY['Crypto Exchange', 'International Wire', 'Luxury Watches', 'Electronics Mega Store',
                      'Online Casino', 'Gift Card Hub'])[1 + (s.account_id / 3) % 6] AS merchant,
               NOW() - (s.account_id % 47) * INTERVAL '1 hour' AS transaction_time
        FROM stats s
    ),
    scored AS (
        SELECT c.*,
               LEAST(100,
                     CASE WHEN c.amount > c.spending_limit THEN 50 ELSE 0 END
                   + CASE WHEN c.history_count >= 3 AND c.amount > c.average_amount * 3 THEN 30 ELSE 0 END
                   + CASE WHEN c.amount >= 50000 THEN 20 ELSE 0 END) AS score,
               concat_ws('; ',
                   CASE WHEN c.amount > c.spending_limit
                        THEN 'Exceeds account spending limit of ' || c.spending_limit::text END,
                   CASE WHEN c.history_count >= 3 AND c.amount > c.average_amount * 3
                        THEN 'Over 3x the account''s average of ' || ROUND(c.average_amount, 2)::text END,
                   CASE WHEN c.amount >= 50000
                        THEN 'At or above large-transaction threshold of 50000' END) AS reasons
        FROM candidates c
    ),
    inserted AS (
        INSERT INTO transactions (account_id, amount, merchant, transaction_time)
        SELECT account_id, amount, merchant, transaction_time FROM scored
        RETURNING id, account_id
    )
    INSERT INTO risk_scores (transaction_id, score, risk_level, flagged, reasons)
    SELECT i.id,
           s.score,
           CASE WHEN s.score >= 50 THEN 'HIGH' WHEN s.score >= 25 THEN 'MEDIUM' ELSE 'LOW' END,
           s.score >= 50,
           NULLIF(s.reasons, '')
    FROM inserted i
    JOIN scored s ON s.account_id = i.account_id;
END $$;
