# plugin-arch-grpc-server-java

> :warning: **If you are new to AccelByte Cloud Service Customization gRPC Plugin Architecture**: Start reading from `OVERVIEW.md` in `plugin-arch-grpc-dependencies` repository to get the full context.

Justice service customization using gRPC plugin architecture - Server (Java).

## Prerequisites

Windows 10 WSL2 or Linux Ubuntu 20.04 with the following tools installed.

- bash
- docker
- docker-compose
- make
- jdk 17

## Setup

1. Create a docker compose `.env` file based on `.env.template` file. 
2. Fill in the required environment variables in `.env` file.

## Building

Build the project and create a docker image for the current platform in one go.

```
make build image
```

For more details about the command, see [Makefile](Makefile).

## Running

Use the following command to run the project.

```
docker-compose up
```

## Advanced

### Building Multi-Arch Docker Image

Build the project and create a multi-arch docker image in one go.

```
make build imagex
```

For more details about the command, see [Makefile](Makefile).

### Running Docker Image Version Locally with Loki Driver

Install Loki Docker plugin.

```
docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions
```
Then, use the following command.

```
docker run --rm --log-driver=loki \
--log-opt loki-url="https://${LOKI_USERNAME}:${LOKI_PASSWORD}@logs-prod3.grafana.net/loki/api/v1/push" \
--log-opt loki-retries=5 \
--log-opt loki-batch-size=400 \
--name  mmgrpc-server-springboot \
--add-host=host.docker.internal:host-gateway\
-p6565:6565 -p8080:8080 \
-eJAVA_OPTS='-javaagent:aws-opentelemetry-agent.jar \
-eOTEL_EXPORTER_ZIPKIN_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
-eOTEL_TRACES_EXPORTER=zipkin \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
-eAPP_SECURITY_CLIENT_ID=${APP_SECURITY_CLIENT_ID} \
-eAPP_SECURITY_CLIENT_SECRET=${APP_SECURITY_CLIENT_SECRET} \
mmgrpc-server-springboot
```
