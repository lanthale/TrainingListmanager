-- Migration 003: Ablaufdatum für bestehende, noch unbenutzte Einladungen nachtragen.
-- Neue Einladungen bekommen es automatisch von der API:
--   Ende des Tages nach Trainingsbeginn, ohne Trainingsdatum 90 Tage ab Erstellung.
-- Einmalig in phpMyAdmin ausführen.

UPDATE invitations i
  JOIN trainings t ON t.id = i.training_id
   SET i.expires_at = DATE_ADD(DATE(t.starts_at), INTERVAL 2 DAY)
 WHERE i.used_at IS NULL
   AND i.expires_at IS NULL
   AND t.starts_at IS NOT NULL;

UPDATE invitations
   SET expires_at = DATE_ADD(created_at, INTERVAL 90 DAY)
 WHERE used_at IS NULL
   AND expires_at IS NULL;
