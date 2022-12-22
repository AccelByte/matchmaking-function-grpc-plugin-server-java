# matchmaking-function-grpc-plugin-server-java

> :warning: **If you are new to AccelByte Cloud Service Customization gRPC Plugin Architecture**: Start reading from `OVERVIEW.md` in `grpc-plugin-dependencies` repository to get the full context.

Justice service customization using gRPC plugin architecture - Server (Java).

## Prerequisites

1. Windows 10 WSL2 or Linux Ubuntu 20.04 with the following tools installed.

    a. bash

    b. docker

    c. docker-compose v2

    d. make

    e. jdk 17

2. AccelByte Cloud demo environment.

    a. Base URL: https://demo.accelbyte.io.

    b. [Create a Game Namespace](https://docs.accelbyte.io/esg/uam/namespaces.html#tutorials) if you don't have one yet. Keep the `Namespace ID`.

    c. [Create an OAuth Client](https://docs.accelbyte.io/guides/access/iam-client.html) with confidential client type and give it `read` permission to resource `NAMESPACE:{namespace}:MMV2GRPCSERVICE`. Keep the `Client ID` and `Client Secret`.

## Setup

Create a docker compose `.env` file based on `.env.template` file and fill in the required environment variables in `.env` file.

```
AB_BASE_URL=https://demo.accelbyte.io   # Base URL
AB_CLIENT_ID=xxxxxxxxxx                 # Client ID
AB_CLIENT_SECRET=xxxxxxxxxx             # Client Secret
AB_NAMESPACE=xxxxxxxxxx                 # Namespace ID
```

> :exclamation: **For the server and client**: Use the same Base URL, Client ID, Client Secret, and Namespace ID.

## Building

To build the application, use the following command.

```
make build
```

To build and create a docker image of the application, use the following command.

```
make image
```

For more details about the command, see [Makefile](Makefile).

## Running

To run the docker image of the application which has been created beforehand, use the following command.

```
docker-compose up
```

OR

To build, create a docker image, and run the application in one go, use the following command.

```
docker-compose up --build
```

## Advanced

### Building Multi-Arch Docker Image

To create a multi-arch docker image of the project, use the following command.

```
make imagex
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
--name  plugin-arch-grpc-server-java-app \
--add-host=host.docker.internal:host-gateway\
-p6565:6565 -p8080:8080 \
-eJAVA_OPTS='-javaagent:aws-opentelemetry-agent.jar \
-eOTEL_EXPORTER_ZIPKIN_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
-eOTEL_TRACES_EXPORTER=zipkin \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
-eAB_CLIENT_ID=${AB_CLIENT_ID} \
-eAB_CLIENT_SECRET=${AB_CLIENT_SECRET} \
plugin-arch-grpc-server-java-app
```
