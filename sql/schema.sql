-- TrainingListmanager – DB-Schema (MySQL 5.7+/8.x, MariaDB 10.3+)
-- Einspielen z. B. über phpMyAdmin in die Datenbank dbs16347872.

SET NAMES utf8mb4;

-- Trainings werden vom separaten Client angelegt.
CREATE TABLE IF NOT EXISTS trainings (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    description TEXT NULL,
    starts_at   DATETIME NULL,
    location    VARCHAR(200) NULL,
    active      TINYINT(1) NOT NULL DEFAULT 1,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- QR-Codes: einer pro Training (email NULL, mehrfach verwendbar, token = Klartext für den
-- PNG-Export im Client). Gesucht wird über token_hash (SHA-256, hex).
-- Ältere persönliche Einladungen (email gesetzt, token NULL) gelten nur für eine Registrierung.
CREATE TABLE IF NOT EXISTS invitations (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_id INT UNSIGNED NOT NULL,
    email       VARCHAR(254) NULL,
    token_hash  CHAR(64) NOT NULL,
    token       CHAR(64) NULL,
    sent_at     DATETIME NULL,
    used_at     DATETIME NULL,
    expires_at  DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_invitations_token_hash (token_hash),
    KEY idx_invitations_training (training_id),
    CONSTRAINT fk_invitations_training FOREIGN KEY (training_id)
        REFERENCES trainings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Registrierungen der Teilnehmer. consent_at = Zeitpunkt der Einwilligung (DSGVO).
CREATE TABLE IF NOT EXISTS registrations (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_id   INT UNSIGNED NOT NULL,
    invitation_id INT UNSIGNED NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    job_position  VARCHAR(150) NOT NULL,
    dealer        VARCHAR(200) NOT NULL,
    consent_at    DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_registrations_invitation (invitation_id),
    KEY idx_registrations_training (training_id),
    CONSTRAINT fk_registrations_training FOREIGN KEY (training_id)
        REFERENCES trainings (id) ON DELETE CASCADE,
    CONSTRAINT fk_registrations_invitation FOREIGN KEY (invitation_id)
        REFERENCES invitations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
