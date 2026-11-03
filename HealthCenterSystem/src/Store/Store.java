package Store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import Patient.Patient;

/** Serializable application state; new fields are compatible with earlier .ser data. */
public class Store implements Serializable {
    private static final long serialVersionUID = 1L;
    private int next = 1;
    private int served = 0;
    private final ArrayList<Patient> patients = new ArrayList<>();
    private final ArrayList<String[]> logs = new ArrayList<>();
    // Non-final: legacy serialized Stores did not contain this field.
    private ArrayList<Patient> history;

    public int nextQueueNumber() { return next++; }
    public int getServed() { return served; }
    public void incrementServed() { served++; }
    public List<Patient> getPatients() { return patients; }
    public List<String[]> getLogs() { return logs; }
    public List<Patient> getHistory() {
        if (history == null) history = new ArrayList<>();
        return history;
    }
    public void setNext(int next) { this.next = next; }
    public void setServed(int served) { this.served = served; }
}
