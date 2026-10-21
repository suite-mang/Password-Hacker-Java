# Password Hacker (Java)

A solution to the **Password Hacker** project from the JetBrains Academy / Hyperskill
Java track. It's a small socket client that "hacks" a login/password-protected server
by exploiting a progression of weaknesses, culminating in a **timing side-channel attack**.

The program connects to a server (`host port` given as program arguments), communicates
over a simple `DataInputStream`/`DataOutputStream` + JSON protocol, and recovers a valid
`login` / `password` pair, which it prints as JSON.

```
> java hacker.Main localhost 9090
{
    "login": "superuser",
    "password": "aDgT9tq1PU0"
}
```

## How the attack works

The server is hardened so the password is random and can't be found in any dictionary.
But two weaknesses remain:

1. **The login is a known dictionary word.** The server validates the login *before* the
   password, so any reply other than `Wrong login!` means the login is correct. The client
   simply tries each entry from the logins dictionary until it hits one.

2. **A timing leak in the password check.** When a guessed password is a correct *prefix*
   of the real one, the server "catches an exception" and stalls for ~100 ms before replying
   `Wrong password!` — the same message it returns for a wrong guess. The delay is the leak.

The client recovers the password one character at a time: for each position it tries every
character in `a-zA-Z0-9`, times each reply with `System.nanoTime()`, and keeps the character
whose reply was **slowest** (the correct prefix). It stops as soon as the server returns
`Connection success!`. This turns an impossible brute force into a few hundred requests.

The solver also treats an explicit `"Exception happened during login"` reply as a prefix
hit, so it works against both the timing-based and message-based variants of the server.

## Project layout

```
.
├── build.gradle, settings.gradle, gradlew…   # Gradle wrapper (Hyperskill course setup)
└── Password Hacker (Java)/task/
    ├── src/hacker/Main.java                   # the solution (client)
    ├── test/HackingTests.java, ServerHack.java # Hyperskill grader + mock server
    └── debugtools/DebugServer.java            # local practice server (not graded)
```

> `debugtools/` lives outside `src/`, so the grader never compiles it — that keeps it from
> tripping the "only one class may have a `main` method" check while staying available for
> local testing.

## Build & run

Requires a JDK (17+). From the repository root:

```bash
# compile the solution
javac -d out "Password Hacker (Java)/task/src/hacker/Main.java"

# run it against a server listening on host:port
java -cp out hacker.Main localhost 9090
```

To run the official Hyperskill test suite:

```bash
./gradlew ":Password_Hacker__Java_-task:test"
```

## Manual testing (no real server needed)

`DebugServer` mirrors the grader's protocol — including the ~100 ms prefix stall — and prints
the secret it chose, so you can watch the attack work end to end. From the `task` folder, in
two terminals:

```bash
# terminal 1 — start the practice server
javac -d debugtools/out debugtools/DebugServer.java
java  -cp debugtools/out DebugServer

# terminal 2 — run the hacker against it
javac -d out src/hacker/Main.java
java  -cp out hacker.Main localhost 9090
```

The server prints `Cracked after N requests.` when the client guesses correctly.
