import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hyperskill.hstest.stage.StageTest;
import org.hyperskill.hstest.testcase.CheckResult;
import org.hyperskill.hstest.testcase.TestCase;

import java.io.IOException;
import java.util.List;
import java.util.Random;


public class HackingTests extends StageTest {
  String abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
  boolean ready = false;
  ServerHack serverHack = null;
  Thread serverThread = null;
  String password = null;
  String login = null;

  String randomLogin() {
    String[] logins = new String[]{
            "admin", "Admin", "admin1", "admin2", "admin3",
            "user1", "user2", "root", "default", "new_user",
            "some_user", "new_admin", "administrator",
            "Administrator", "superuser", "super", "su", "alex",
            "suser", "rootuser", "adminadmin", "useruser",
            "superadmin", "username", "username1"
    };
    Random ran = new Random();
    return logins[ran.nextInt(logins.length)];
  }

  String randomPassword() {
    Random ran = new Random();
    int length = ran.nextInt(5) + 6;
    String ret = "";
    for (int i = 0; i < length; i++) {
      ret = ret.concat(String.valueOf(abc.charAt(ran.nextInt(abc.length()))));
    }
    return ret;
  }

  void startServer() throws IOException {
    serverHack = new ServerHack(this);
    serverThread = new Thread(serverHack);
    serverThread.start();

    while (!ready) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ignored) {
      }
    }
  }

  void stopServer() throws InterruptedException {
    serverHack.disconnect();
    serverThread.join();
  }

  @Override
  public List<TestCase<String[]>> generate() {
    try {
      startServer();
    } catch (IOException ignored) {
    }
    password = randomPassword();
    login = randomLogin();
    return List.of(new TestCase<String[]>()
            .addArguments("localhost", "9090")
            .setAttach(new String[]{password, login})
            .setTimeLimit(25000)
    );
  }

  public CheckResult check(String reply, Object attach) {
    try {
      stopServer();
    } catch (Exception ignored) {
    }
    if (serverHack == null || !serverHack.connected) {
      return CheckResult.wrong("You didn't connect to the server");
    }
    if (serverHack.message.size() == 0) {
      return CheckResult.wrong("You sent nothing to the server");
    }
    if (reply.length() == 0 || reply.split("\n").length == 0) {
      return CheckResult.wrong("You did not print anything");
    }

    String[] attachStr = (String[]) attach;

    String realPassword = attachStr[0];
    String realLogin = attachStr[1];

    JsonObject jsonReply;

    try {
      jsonReply = new Gson().fromJson(reply, JsonObject.class);
    } catch (Exception e) {
      return CheckResult.wrong("The output of your program is not a valid JSON:\n" + reply);
    }

    JsonElement passwordElement = jsonReply.get("password");
    if (passwordElement == null) {
      return CheckResult.wrong("The output of your program did not contain the field \"password\":\n" + reply);
    }
    JsonElement loginElement = jsonReply.get("login");
    if (loginElement == null) {
      return CheckResult.wrong("The output of your program did not contain the field \"login\":\n" + reply);
    }

    String password_ = passwordElement.getAsString();
    String login_ = loginElement.getAsString();

    if (!login_.equals(realLogin)) {
      return CheckResult.wrong("The login you printed is not correct");
    }
    if (!password_.equals(realPassword)) {
      return CheckResult.wrong("The password you printed is not correct");
    }

    boolean findFirstLetter = false;

    for (String i : serverHack.message) {
      jsonReply = new Gson().fromJson(i, JsonObject.class);
      String pas = jsonReply.get("password").getAsString();
      String log = jsonReply.get("login").getAsString();
      if (!findFirstLetter && pas.length() == 1 && log == realLogin && realPassword.startsWith(pas)) {
        findFirstLetter = true;
      }
      if (findFirstLetter) {
        if (!log.equals(realLogin)) {
          return CheckResult.wrong("You should find a correct login and then use only it");
        }
        if (pas.charAt(0) != realPassword.charAt(0)) {
          return CheckResult.wrong("When you find a first letter you should then start your passwords with it");
        }
        if (pas.length() > 1) {
          if (!pas.substring(0, pas.length() - 1).equals(realPassword.substring(0, pas.length() - 1))) {
            return CheckResult.wrong(
                    "You have already found the first " + (pas.length() - 1) + " letters of the password. Use them as a" +
                            " beginning"
            );
          }
        }
      }
    }
    return CheckResult.correct();
  }
}