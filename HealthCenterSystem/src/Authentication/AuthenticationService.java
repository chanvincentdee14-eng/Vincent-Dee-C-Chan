package Authentication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local login with salted PBKDF2 password hashes stored in a users file (default database/users.txt,
 * or the path in the hc.users system property). Passwords are never stored in readable form.
 */
public class AuthenticationService {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final File usersFile;

    public AuthenticationService() {
        this(new File(System.getProperty("hc.users", "database" + File.separator + "users.txt")));
    }

    public AuthenticationService(File usersFile) {
        this.usersFile = usersFile;
        try {
            if (!usersFile.isFile()) createFirstRunAccounts();
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Could not create the users file: " + usersFile.getAbsolutePath(), ex);
        }
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        try {
            String[] record = readUsers().get(username.trim().toLowerCase(Locale.ROOT));
            if (record == null) return false;
            int iterations = Integer.parseInt(record[1]);
            byte[] salt = Base64.getDecoder().decode(record[2]);
            byte[] expected = Base64.getDecoder().decode(record[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            return false;
        }
    }

    /** Sets or replaces an account's password. Passwords must be at least 8 characters. */
    public synchronized void setPassword(String username, String password) throws IOException, GeneralSecurityException {
        if (username == null || username.trim().isEmpty() || username.contains("|"))
            throw new IllegalArgumentException("Invalid username.");
        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, ITERATIONS);
        Map<String, String[]> users = readUsers();
        users.put(username.trim().toLowerCase(Locale.ROOT), new String[]{
                username.trim(), Integer.toString(ITERATIONS),
                Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(hash)});
        writeUsers(users);
    }

    /** Creates the original default accounts (admin/admin123, staff/staff123) when no users file exists. */
    private void createFirstRunAccounts() throws IOException, GeneralSecurityException {
        setPassword("admin", "admin123");
        setPassword("staff", "staff123");
    }

    private File parentOrCurrent() {
        File parent = usersFile.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return parent == null ? new File(".") : parent;
    }

    private Map<String, String[]> readUsers() throws IOException {
        Map<String, String[]> users = new LinkedHashMap<>();
        if (!usersFile.isFile()) return users;
        for (String line : Files.readAllLines(usersFile.toPath(), StandardCharsets.UTF_8)) {
            String[] parts = line.split("\\|", -1);
            if (parts.length == 4) users.put(parts[0].trim().toLowerCase(Locale.ROOT), parts);
        }
        return users;
    }

    private void writeUsers(Map<String, String[]> users) throws IOException {
        parentOrCurrent();
        StringBuilder text = new StringBuilder();
        for (String[] parts : users.values()) text.append(String.join("|", parts)).append('\n');
        Files.write(usersFile.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

}
