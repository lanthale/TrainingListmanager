package de.mindarchitects.tlm.api;

import java.util.List;

/** Datentypen der Server-API (JSON in snake_case, Datumswerte als "yyyy-MM-dd HH:mm:ss"). */
public final class Model {
    private Model() {}

    public record Training(long id, String title, String description, String startsAt, String location,
                           boolean active, String createdAt, int registered) {}

    /** QR-Code eines Trainings: url enthält den Token, expiresAt das Ablaufdatum. */
    public record Code(String url, String expiresAt, String createdAt) {}

    public record Registration(long id, String firstName, String lastName, String jobPosition, String dealer,
                               String consentAt, String createdAt) {}

    /** code ist null, wenn das Training (noch) keinen QR-Code hat. */
    public record TrainingDetail(Training training, Code code, List<Registration> registrations) {}

    public record CreatedTraining(long id, Code code) {}

    record CodeResult(Code code) {}

    record TrainingList(List<Training> trainings) {}
}
