CREATE TABLE IF NOT EXISTS customer (
    customer_id     BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    date_of_birth   DATE         NOT NULL,
    mobile          CHAR(10)     NOT NULL,
    email           VARCHAR(120),
    address         VARCHAR(255),
    kyc_type        VARCHAR(20),
    kyc_number      VARCHAR(30),
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME,
    PRIMARY KEY (customer_id),
    CONSTRAINT uk_customer_mobile UNIQUE (mobile),
    CONSTRAINT chk_customer_mobile CHECK (mobile REGEXP '^[0-9]{10}$')
);

CREATE TABLE IF NOT EXISTS loan_application (
    application_id               BIGINT        NOT NULL AUTO_INCREMENT,
    application_number           VARCHAR(40)   NOT NULL,
    customer_id                  BIGINT        NOT NULL,
    product                      VARCHAR(20)   NOT NULL,
    branch_code                  VARCHAR(10)   NOT NULL,
    branch_name                  VARCHAR(60)   NOT NULL,
    requested_amount             DECIMAL(15,2) NOT NULL,
    tenure_months                INT           NOT NULL,
    monthly_income               DECIMAL(15,2) NOT NULL,
    existing_monthly_obligations DECIMAL(15,2) NOT NULL DEFAULT 0,
    co_borrower_name             VARCHAR(100),
    annual_interest_rate         DECIMAL(5,2),
    proposed_emi                 DECIMAL(15,2),
    foir_percentage              DECIMAL(7,2),
    eligible                     BOOLEAN,
    eligibility_reasons          VARCHAR(1000),
    status                       VARCHAR(20)   NOT NULL,
    approved_amount              DECIMAL(15,2),
    application_form_path        VARCHAR(255),
    created_by                   VARCHAR(20)   NOT NULL,
    created_at                   DATETIME      NOT NULL,
    updated_at                   DATETIME,
    submitted_at                 DATETIME,
    decided_at                   DATETIME,
    reviewer_code                VARCHAR(20),
    decision_reason              VARCHAR(500),
    credit_decided_at            DATETIME,
    credit_approver_code         VARCHAR(20),
    active_key                   VARCHAR(40) GENERATED ALWAYS AS (
        CASE WHEN status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', 'APPROVED')
             THEN CONCAT(customer_id, ':', product) END) STORED,
    PRIMARY KEY (application_id),
    CONSTRAINT uk_app_number UNIQUE (application_number),
    CONSTRAINT uk_app_active_product UNIQUE (active_key),
    CONSTRAINT fk_app_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT chk_app_product CHECK (product IN ('PERSONAL', 'EDUCATION')),
    CONSTRAINT chk_app_status CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', 'APPROVED',
                                                'REJECTED', 'DISBURSED', 'CORE_BANKING_PUSH', 'EXIT')),
    CONSTRAINT chk_app_amount CHECK (requested_amount > 0),
    CONSTRAINT chk_app_tenure CHECK (tenure_months > 0),
    CONSTRAINT chk_app_income CHECK (monthly_income > 0),
    CONSTRAINT chk_app_obligations CHECK (existing_monthly_obligations >= 0),
    INDEX idx_app_status (status),
    INDEX idx_app_submitted (submitted_at)
);

CREATE TABLE IF NOT EXISTS disbursement (
    disbursement_id BIGINT        NOT NULL AUTO_INCREMENT,
    application_id  BIGINT        NOT NULL,
    reference       VARCHAR(40)   NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    disbursed_at    DATETIME      NOT NULL,
    disbursed_by    VARCHAR(20)   NOT NULL,
    PRIMARY KEY (disbursement_id),
    CONSTRAINT uk_disb_application UNIQUE (application_id),
    CONSTRAINT uk_disb_reference UNIQUE (reference),
    CONSTRAINT fk_disb_application FOREIGN KEY (application_id) REFERENCES loan_application (application_id),
    CONSTRAINT chk_disb_amount CHECK (amount > 0)
);

CREATE TABLE IF NOT EXISTS application_history (
    history_id     BIGINT       NOT NULL AUTO_INCREMENT,
    application_id BIGINT       NOT NULL,
    action         VARCHAR(30)  NOT NULL,
    old_status     VARCHAR(20),
    new_status     VARCHAR(20)  NOT NULL,
    performed_by   VARCHAR(20)  NOT NULL,
    performed_at   DATETIME     NOT NULL,
    remarks        VARCHAR(1000),
    PRIMARY KEY (history_id),
    CONSTRAINT fk_hist_application FOREIGN KEY (application_id) REFERENCES loan_application (application_id),
    INDEX idx_hist_application (application_id, performed_at)
);

CREATE TABLE IF NOT EXISTS system_alert (
    alert_id       BIGINT       NOT NULL AUTO_INCREMENT,
    application_id BIGINT       NOT NULL,
    alert_type     VARCHAR(30)  NOT NULL,
    message        VARCHAR(255),
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (alert_id),
    CONSTRAINT uk_alert_app_type UNIQUE (application_id, alert_type),
    CONSTRAINT fk_alert_application FOREIGN KEY (application_id) REFERENCES loan_application (application_id)
);

CREATE TABLE IF NOT EXISTS utility_run (
    run_id                 BIGINT        NOT NULL AUTO_INCREMENT,
    utility_name           VARCHAR(30)   NOT NULL,
    trigger_type           VARCHAR(20)   NOT NULL,
    business_date          DATE          NOT NULL,
    started_at             DATETIME      NOT NULL,
    completed_at           DATETIME,
    result                 VARCHAR(20)   NOT NULL,
    applications_processed INT           NOT NULL DEFAULT 0,
    alerts_created         INT           NOT NULL DEFAULT 0,
    output_file            VARCHAR(255),
    error_message          VARCHAR(1000),
    PRIMARY KEY (run_id),
    INDEX idx_run_started (started_at)
);

CREATE TABLE IF NOT EXISTS NG_CORE_BANKING (
    core_id          BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id      BIGINT        NOT NULL,
    application_id   BIGINT        NOT NULL,
    customer_name    VARCHAR(100)  NOT NULL,
    address          VARCHAR(255),
    date_of_birth    DATE          NOT NULL,
    kyc_details      VARCHAR(100),
    approved_amount  DECIMAL(15,2) NOT NULL,
    disbursed_amount DECIMAL(15,2) NOT NULL,
    disbursed_date   DATE          NOT NULL,
    pushed_at        DATETIME      NOT NULL,
    PRIMARY KEY (core_id),
    CONSTRAINT uk_core_application UNIQUE (application_id),
    CONSTRAINT fk_core_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_core_application FOREIGN KEY (application_id) REFERENCES loan_application (application_id)
);
