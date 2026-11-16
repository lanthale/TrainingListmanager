package de.mindarchitects.tlm.api;

import java.util.List;

/** Datentypen der Server-API (JSON in snake_case, Datumswerte als "yyyy-MM-dd HH:mm:ss"). */
public final class Model {
    private Model() {}

    public record Training(long id, String title, String description, String startsAt, String location,
                           boolean active, String createdAt, int invited, int sent, int registered) {}

    public record Invitation(long id, String email, String sentAt, String usedAt, String expiresAt,
                             String createdAt) {}

    public record Registration(long id, long invitationId, String firstName, String lastName,
                               String jobPosition, String dealer, String createdAt, String email) {}

    public record TrainingDetail(Training training, List<Invitation> invitations,
                                 List<Registration> registrations) {}

    /** Neu erzeugte Einladung; die URL enthält den Klartext-Token und existiert nur in dieser Antwort. */
    public record CreatedInvitation(long id, String email, String url) {}

    public record Skipped(String email, String reason) {}

    public record CreateInvitationsResult(List<CreatedInvitation> created, List<Skipped> skipped) {}

    record TrainingList(List<Training> trainings) {}

    record IdResult(long id) {}
}
