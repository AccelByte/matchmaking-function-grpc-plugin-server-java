# matchmaking-function-grpc-plugin-server-java

An Extend Override app for the **matchmaking function** written in Java. AGS calls this gRPC server to execute custom match logic instead of the default matchmaking behavior.

This is a template project — clone it, replace the sample logic in the service implementation, and deploy.

## Build & Test

```bash
./gradlew build                      # Build with Gradle
./gradlew test                       # Run tests
docker compose up --build            # Run locally with Docker
./gradlew generateProto              # Regenerate proto code
```

## Architecture

AGS invokes this app's gRPC methods instead of its default logic:

```
Game Client → AGS → [gRPC] → This App → Response → AGS
```

The sample implementation uses a rule-based matchmaker that validates tickets against configurable game rules, enriches tickets with stat data, and builds matches via gRPC bidirectional streaming.

### Key Files

| Path | Purpose |
|---|---|
| `src/main/java/net/accelbyte/matchmaking/function/grpc/server/App.java` | Entry point — starts gRPC server, wires interceptors and observability |
| `src/main/java/net/accelbyte/matchmaking/function/grpc/server/MatchmakingFunctionService.java` | **Service implementation** — your custom logic goes here |
| `src/main/proto/matchFunction.proto` | gRPC service definition (AccelByte-provided, do not modify) |
| `docker-compose.yaml` | Local development setup |
| `.env.template` | Environment variable template |

### Streaming Notes

The `MakeMatches` and `BackfillMatches` methods use gRPC bidirectional streaming: AGS sends tickets as a stream, and the server sends back match results as they are formed. Internally, Go channels feed tickets from the receive goroutine to the matchmaker, and a separate goroutine sends results back. This concurrent pipeline processes tickets as they arrive rather than waiting for all tickets first.

## Rules

See `.agents/rules/` for coding conventions, commit standards, and proto file policies.

## Environment

Copy `.env.template` to `.env` and fill in your credentials.

| Variable | Description |
|---|---|
| `AB_BASE_URL` | AccelByte base URL (e.g. `https://test.accelbyte.io`) |
| `AB_CLIENT_ID` | OAuth client ID |
| `AB_CLIENT_SECRET` | OAuth client secret |
| `AB_NAMESPACE` | Target namespace |
| `PLUGIN_GRPC_SERVER_AUTH_ENABLED` | Enable gRPC auth (`true` by default) |

## Dependencies

- [AccelByte Java SDK](https://github.com/AccelByte/accelbyte-java-sdk) (`net.accelbyte.sdk:sdk`) — AGS platform SDK and gRPC plugin utilities
