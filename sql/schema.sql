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

-- Persönliche Einladungen (eine pro Teilnehmer, ein QR-Code pro Einladung).
-- Im QR-Code steht der Klartext-Token, hier nur dessen SHA-256-Hash (hex).
CREATE TABLE IF NOT EXISTS invitations (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_id INT UNSIGNED NOT NULL,
    email       VARCHAR(254) NULL,
    token_hash  CHAR(64) NOT NULL,
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

-- Registrierungen der Teilnehmer (max. eine pro Einladung).
CREATE TABLE IF NOT EXISTS registrations (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_id   INT UNSIGNED NOT NULL,
    invitation_id INT UNSIGNED NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    job_position  VARCHAR(150) NOT NULL,
    dealer        VARCHAR(200) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_registrations_invitation (invitation_id),
    KEY idx_registrations_training (training_id),
    CONSTRAINT fk_registrations_training FOREIGN KEY (training_id)
        REFERENCES trainings (id) ON DELETE CASCADE,
    CONSTRAINT fk_registrations_invitation FOREIGN KEY (invitation_id)
        REFERENCES invitations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
