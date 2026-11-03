package QueueManager;

import Patient.Patient;
import Store.Store;
import Data.DataStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** FIFO rules and all CRUD mutations, independent of the Swing user interface. */
public class QueueManager {
    private final Store store;
    private final DataStorage storage;
    public QueueManager(Store store, DataStorage storage) { this.store = store; this.storage = storage; }
    public Store getStore() { return store; }
    public Patient find(String id) {
        if (id == null) return null;
        for (Patient patient : store.getPatients())
            if (id.equals(patient.getId())) return patient;
        return null;
    }
    public Patient current() {
        for (Patient p : store.getPatients()) if ("Serving".equals(p.getStatus())) return p;
        return null;
    }
    public Patient next() {
        for (Patient p : store.getPatients()) if ("Waiting".equals(p.getStatus())) return p;
        return null;
    }
    public Patient enqueue(String name, String contact, String birthday, String address, String actor) {
        validate(name, contact, birthday, address);
        String id = String.format("Q%03d", store.nextQueueNumber());
        Patient p = new Patient(id, name.trim(), safe(contact), safe(birthday), safe(address), "Waiting");
        store.getPatients().add(p);
        store.getHistory().add(p);
        if (current() == null) p.setStatus("Serving");
        log("Enqueue", p, "Checked in and added to FIFO queue", actor);
        storage.save(store);
        return p;
    }
    public void update(Patient p, String name, String contact, String birthday, String address, String actor) {
        if (p == null || !store.getPatients().contains(p))
            throw new IllegalArgumentException("Select an active patient first.");
        validate(name, contact, birthday, address);
        p.setName(name.trim()); p.setContact(safe(contact));
        p.setBirthday(safe(birthday)); p.setAddress(safe(address));
        log("Update", p, "Patient information updated", actor);
        storage.save(store);
    }
    public void promote(String actor) {
        if (current() == null) {
            Patient p = next();
            if (p != null) {
                p.setStatus("Serving");
                log("Serve", p, "Promoted to serving", actor);
            }
        }
    }
    public void serve(Patient p, String actor) {
        if (p == null || p != next() || current() != null)
            throw new IllegalArgumentException("Only the first waiting patient may be called.");
        p.setStatus("Serving");
        log("Serve", p, "Patient called for consultation", actor);
        storage.save(store);
    }
    public void finish(Patient p, String actor) {
        if (p == null || !"Serving".equals(p.getStatus()))
            throw new IllegalArgumentException("Only the serving patient can be completed.");
        p.markFinished("Completed");
        store.incrementServed();
        store.getPatients().remove(p);
        log("Done / Dequeue", p, "Visit completed; retained in Records", actor);
        promote(actor);
        storage.save(store);
    }
    /** Put the serving patient, or the next waiting patient, on hold. Holding the serving patient advances the line. */
    public void hold(Patient p, String actor) {
        if (p == null || !store.getPatients().contains(p))
            throw new IllegalArgumentException("Select an active patient first.");
        boolean wasServing = "Serving".equals(p.getStatus());
        if (!wasServing && p != next())
            throw new IllegalArgumentException("Only the serving patient or the next waiting patient can be placed on hold.");
        p.setStatus("Skipped");
        store.getPatients().remove(p);
        store.getPatients().add(p);
        log("Skip", p, wasServing ? "Serving patient placed on hold (can rejoin queue)"
                : "Next waiting patient placed on hold (can rejoin queue)", actor);
        promote(actor);
        storage.save(store);
    }

    /** Place the person being served on hold and advance the line. */
    public void skipServing(String actor) {
        Patient serving = current();
        if (serving == null) throw new IllegalArgumentException("There is no patient serving to place on hold.");
        hold(serving, actor);
    }

    /** Put a previously skipped visit at the end of the active waiting line. */
    public void returnToQueue(Patient patient, String actor) {
        if (patient == null || !store.getPatients().contains(patient)
                || !"Skipped".equals(patient.getStatus()))
            throw new IllegalArgumentException("Select a patient whose status is On Hold.");
        store.getPatients().remove(patient);
        store.getPatients().add(patient);
        patient.setStatus("Waiting");
        log("Return", patient, "Patient rejoined the end of the waiting line", actor);
        promote(actor);
        storage.save(store);
    }

    /** Warn staff about possible repeat check-ins without blocking legitimate repeat visits. */
    public Patient possibleDuplicate(String fullName, String phone, String birth) {
        if (fullName == null) return null;
        String normalized = fullName.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        String phoneDigits = phone == null ? "" : phone.replaceAll("\\D", "");
        for (Patient p : store.getPatients()) {
            if (!p.getName().trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT).equals(normalized)) continue;
            if (!phoneDigits.isEmpty() && phoneDigits.equals(p.getContact().replaceAll("\\D", ""))) return p;
            if (birth != null && !birth.isEmpty() && birth.equals(p.getBirthday())) return p;
        }
        return null;
    }

    public void delete(Patient p, String actor) {
        if (p == null || !store.getPatients().contains(p))
            throw new IllegalArgumentException("Select an active patient first.");
        boolean wasServing = "Serving".equals(p.getStatus());
        p.markFinished("Removed");
        store.getPatients().remove(p);
        log("Delete", p, "Removed from active queue; retained in Records", actor);
        if (wasServing) promote(actor);
        storage.save(store);
    }
    /** A visit leaves the active queue without completing consultation; keep its record. */
    public void dequeue(Patient p, String actor) {
        if (p == null || !store.getPatients().contains(p))
            throw new IllegalArgumentException("Select an active patient first.");
        boolean wasServing = "Serving".equals(p.getStatus());
        p.markFinished("Dequeued");
        store.getPatients().remove(p);
        log("Dequeue", p, "Left queue without completed consultation; visit retained in Records", actor);
        if (wasServing) promote(actor);
        storage.save(store);
    }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static void validate(String name, String contact, String birthday, String address) {
        if (name == null || name.trim().length() < 2)
            throw new IllegalArgumentException("Enter the patient's full name (at least 2 characters).");
        if (name.trim().length() > 100 || name.contains("\n") || name.contains("\r"))
            throw new IllegalArgumentException("Patient name must be on one line and at most 100 characters.");
        String phone = safe(contact);
        if (!phone.isEmpty()) {
            String digits = phone.replaceAll("\\D", "");
            if (!phone.matches("[+0-9() -]+") || digits.length() < 7 || digits.length() > 15)
                throw new IllegalArgumentException("Enter a valid 7–15 digit contact number, or leave it blank.");
        }
        if (safe(address).length() > 250)
            throw new IllegalArgumentException("Address must be at most 250 characters.");
        if (birthday != null && !birthday.trim().isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(birthday.trim());
                if (date.isAfter(LocalDate.now()))
                    throw new IllegalArgumentException("Birthday cannot be in the future.");
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid birthday selection.");
            }
        }
    }
    private void log(String action, Patient p, String detail, String actor) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a"));
        store.getLogs().add(0, new String[]{date, actor == null || actor.isEmpty() ? "System" : actor,
                action, p.getId(), p.getName(), p.getStatus(), detail});
        if (store.getLogs().size() > 300) store.getLogs().remove(store.getLogs().size() - 1);
    }
}
