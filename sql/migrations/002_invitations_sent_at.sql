-- Migration 002: Versandzeitpunkt der Einladungs-Mail (für den Desktop-Client).
-- Einmalig in phpMyAdmin ausführen, wenn schema.sql schon vorher eingespielt war.
ALTER TABLE invitations ADD COLUMN sent_at DATETIME NULL AFTER token_hash;
