package Patient;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Patient visit; a queue number identifies the visit, not a permanent medical identity. */
public class Patient implements Serializable {
    private static final long serialVersionUID = 1L; // Read existing saved projects.
    private String id, name, contact, birthday, address, status;
    private String checkedInAt, completedAt;

    public Patient(String id, String name, String contact, String birthday, String address, String status) {
        this.id = id;
        this.name = name;
        this.contact = contact;
        this.birthday = birthday;
        this.address = address;
        this.status = status;
        this.checkedInAt = LocalDateTime.now().toString();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getContact() { return contact == null ? "" : contact; }
    public String getBirthday() { return birthday == null ? "" : birthday; }
    public String getAddress() { return address == null ? "" : address; }
    public String getStatus() { return status == null ? "Waiting" : status; }
    public String getCheckedInAt() { return checkedInAt; }
    public String getCompletedAt() { return completedAt; }
    public void setName(String value) { name = value; }
    public void setContact(String value) { contact = value; }
    public void setBirthday(String value) { birthday = value; }
    public void setAddress(String value) { address = value; }
    public void setStatus(String value) { status = value; }
    public void setCheckedInAt(String value) { checkedInAt = value; }
    public void setCompletedAt(String value) { completedAt = value; }
    public void markFinished(String newStatus) {
        status = newStatus;
        completedAt = LocalDateTime.now().toString();
    }
}
