import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServerHack implements Runnable {
  HackingTests hacking;

  boolean stopThread = true;
  boolean connected;

  ServerSocket server;
  Socket socket;
  List<String> message = new ArrayList<>();

  DataInputStream inputStream;
  DataOutputStream outputStream;

  Gson gson;
  JsonObject jsonObject;

  public ServerHack(HackingTests hacking) {
    this.hacking = hacking;
    gson = new Gson();
    jsonObject = new JsonObject();
  }

  @Override
  public void run() {
    String address = "localhost";
    int port = 9090;
    try {
      server = new ServerSocket(port, 50, InetAddress.getByName(address));
    } catch (IOException ignored) {
    }
    hacking.ready = true;
    try {
      socket = server.accept();
      socket.setSoTimeout(16000);
      connected = true;

      inputStream = new DataInputStream(socket.getInputStream());
      outputStream = new DataOutputStream(socket.getOutputStream());
      while (stopThread) {
        String msg = inputStream.readUTF();
        message.add(msg);
        if (message.size() > 100_000_000) {
          jsonObject.addProperty("result", "Too many attempts");
          outputStream.writeUTF(gson.toJson(jsonObject));
          break;
        }
        String login_, password_;

        try {
          JsonObject json = new Gson().fromJson(msg, JsonObject.class);
          login_ = json.get("login").getAsString();
          password_ = json.get("password").getAsString();
        } catch (Exception e) {
          jsonObject.addProperty("result", "Bad request!");
          outputStream.writeUTF(gson.toJson(jsonObject));
          continue;
        }

        boolean success = false;
        if (login_.equals(hacking.login)) {
          if (password_.equals(hacking.password)) {
            jsonObject.addProperty("result", "Connection success!");
            success = true;
          }else{
            if (hacking.password.startsWith(password_)) {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ignored) {
              }
            }
            jsonObject.addProperty("result", "Wrong password!");
          }
        } else {
          jsonObject.addProperty("result", "Wrong login!");
        }
        outputStream.writeUTF(gson.toJson(jsonObject));
        if (success) {
          break;
        }
      }
      disconnect();
    } catch (IOException ignored) {
    }
  }

  public void disconnect() {
    stopThread = false;
    try {
      inputStream.close();
      socket.close();
      server.close();
    } catch (IOException ignored) {
    }
  }
}
