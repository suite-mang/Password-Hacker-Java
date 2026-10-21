// Local practice server for manual testing of hacker.Main (timing-vulnerability stage).
// Mirrors the grader's ServerHack: a correct password PREFIX makes the server stall ~100ms
// (it "catches an exception") but still replies "Wrong password!". Parses JSON by hand so it
// needs no Gson. Lives in task/debugtools (NOT under src/) so the grader ignores it.
//
// Manual test (two terminals, from the "task" folder):
//   1)  javac -d debugtools/out debugtools/DebugServer.java
//       java  -cp debugtools/out DebugServer
//   2)  javac -d out src/hacker/Main.java
//       java  -cp out hacker.Main localhost 9090
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DebugServer {

    static final String[] LOGINS = {
            "admin", "Admin", "admin1", "admin2", "admin3", "user1", "user2", "root",
            "default", "new_user", "some_user", "new_admin", "administrator", "Administrator",
            "superuser", "super", "su", "alex", "suser", "rootuser", "adminadmin", "useruser",
            "superadmin", "username", "username1"
    };
    static final String ABC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

    public static void main(String[] args) throws IOException {
        int port = 9090;
        Random ran = new Random();

        String login = LOGINS[ran.nextInt(LOGINS.length)];
        int len = ran.nextInt(5) + 6;                 // 6..10, same as the grader
        StringBuilder pw = new StringBuilder();
        for (int i = 0; i < len; i++) pw.append(ABC.charAt(ran.nextInt(ABC.length())));
        String password = pw.toString();

        System.out.println("[DebugServer] Listening on localhost:" + port);
        System.out.println("[DebugServer] Secret login='" + login + "'  password='" + password + "'");

        Pattern field = Pattern.compile("\"(login|password)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("localhost"));
             Socket socket = server.accept();
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("[DebugServer] Client connected.");
            int attempts = 0;
            while (true) {
                String msg = in.readUTF();
                attempts++;

                String login_ = null, password_ = null;
                Matcher m = field.matcher(msg);
                while (m.find()) {
                    if (m.group(1).equals("login")) login_ = unescape(m.group(2));
                    else password_ = unescape(m.group(2));
                }

                String result;
                boolean success = false;
                if (login_ == null || password_ == null) {
                    result = "Bad request!";
                } else if (login_.equals(login)) {
                    if (password_.equals(password)) {
                        result = "Connection success!";
                        success = true;
                    } else {
                        // Timing leak: a correct prefix stalls, but the message is unchanged.
                        if (password.startsWith(password_)) {
                            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                        }
                        result = "Wrong password!";
                    }
                } else {
                    result = "Wrong login!";
                }

                out.writeUTF("{\"result\": \"" + result + "\"}");
                out.flush();
                if (success) {
                    System.out.println("[DebugServer] Cracked after " + attempts + " requests.");
                    break;
                }
            }
        }
        System.out.println("[DebugServer] Done.");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
