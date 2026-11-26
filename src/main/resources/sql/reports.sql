-- Six reports used by the console and web screens.
-- Each block starts with "-- name:" and uses :named parameters which the app binds as prepared statement values.
-- Passing NULL for an optional filter skips that filter.

-- name: application_register
SELECT a.application_number,
       c.name              AS customer_name,
       a.product,
       a.branch_code,
       a.requested_amount,
       a.proposed_emi,
       a.status,
       a.submitted_at
FROM loan_application a
JOIN customer c ON c.customer_id = a.customer_id
WHERE (:product IS NULL OR a.product = :product)
  AND (:status IS NULL OR a.status = :status)
ORDER BY a.submitted_at IS NULL, a.submitted_at DESC, a.application_id DESC;

-- name: pipeline_by_product_status
SELECT a.product,
       a.status,
       COUNT(*)                AS application_count,
       SUM(a.requested_amount) AS total_requested
FROM loan_application a
GROUP BY a.product, a.status
ORDER BY a.product, a.status;

-- name: pipeline_by_branch_product
SELECT a.branch_code,
       a.branch_name,
       a.product,
       COUNT(*)                AS application_count,
       SUM(a.requested_amount) AS total_requested
FROM loan_application a
GROUP BY a.branch_code, a.branch_name, a.product
ORDER BY a.branch_code, a.product;

-- name: disbursement_summary
SELECT CASE :period
           WHEN 'DAILY'   THEN DATE_FORMAT(d.disbursed_at, '%Y-%m-%d')
           WHEN 'MONTHLY' THEN DATE_FORMAT(d.disbursed_at, '%Y-%m')
           ELSE CONCAT(YEAR(d.disbursed_at), '-Q', QUARTER(d.disbursed_at))
       END              AS period,
       COUNT(*)         AS disbursement_count,
       SUM(d.amount)    AS total_amount
FROM disbursement d
WHERE DATE(d.disbursed_at) BETWEEN :from_date AND :to_date
GROUP BY period
ORDER BY period;

-- name: disbursement_by_branch
SELECT a.branch_code,
       a.branch_name,
       CASE :period
           WHEN 'DAILY'   THEN DATE_FORMAT(d.disbursed_at, '%Y-%m-%d')
           WHEN 'MONTHLY' THEN DATE_FORMAT(d.disbursed_at, '%Y-%m')
           ELSE CONCAT(YEAR(d.disbursed_at), '-Q', QUARTER(d.disbursed_at))
       END              AS period,
       COUNT(*)         AS disbursement_count,
       SUM(d.amount)    AS total_amount
FROM disbursement d
JOIN loan_application a ON a.application_id = d.application_id
WHERE DATE(d.disbursed_at) BETWEEN :from_date AND :to_date
GROUP BY a.branch_code, a.branch_name, period
ORDER BY a.branch_code, period;

-- name: overdue_reviews
SELECT a.application_number,
       c.name                                         AS customer_name,
       c.mobile,
       a.product,
       a.requested_amount,
       a.submitted_at,
       DATEDIFF(:business_date, DATE(a.submitted_at)) AS days_waiting
FROM loan_application a
JOIN customer c ON c.customer_id = a.customer_id
WHERE a.status = 'UNDER_REVIEW'
  AND DATEDIFF(:business_date, DATE(a.submitted_at)) > :overdue_days
ORDER BY days_waiting DESC, a.application_number;

-- name: customers_without_disbursement
SELECT c.customer_id,
       c.name,
       c.mobile,
       COUNT(a.application_id) AS application_count
FROM customer c
JOIN loan_application a ON a.customer_id = c.customer_id
LEFT JOIN disbursement d ON d.application_id = a.application_id
GROUP BY c.customer_id, c.name, c.mobile
HAVING COUNT(d.disbursement_id) = 0
ORDER BY c.customer_id;

-- name: repeat_applicants
SELECT c.customer_id,
       c.name,
       c.mobile,
       COUNT(a.application_id) AS application_count,
       SUM(a.requested_amount) AS total_requested
FROM customer c
JOIN loan_application a ON a.customer_id = c.customer_id
GROUP BY c.customer_id, c.name, c.mobile
HAVING COUNT(a.application_id) > 1
ORDER BY application_count DESC, c.customer_id;
