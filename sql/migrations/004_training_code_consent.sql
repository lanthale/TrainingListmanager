-- Migration 004: ein QR-Code pro Training statt persönlicher Einladungen, Einwilligung speichern.
-- Einmalig in phpMyAdmin ausführen (nach 002 und 003).

-- QR-Code des Trainings: Klartext-Token, damit der Client das PNG jederzeit neu exportieren kann.
-- (Persönliche Einladungen aus früheren Versionen behalten token = NULL.)
ALTER TABLE invitations ADD COLUMN token CHAR(64) NULL AFTER token_hash;

-- Mehrere Registrierungen pro QR-Code erlauben (erst neuen Index anlegen, den braucht der Fremdschlüssel).
ALTER TABLE registrations ADD KEY idx_registrations_invitation (invitation_id);
ALTER TABLE registrations DROP INDEX uq_registrations_invitation;

-- Zeitpunkt der Einwilligung in die Datenverarbeitung (DSGVO Art. 7 Abs. 1).
ALTER TABLE registrations ADD COLUMN consent_at DATETIME NULL AFTER dealer;
