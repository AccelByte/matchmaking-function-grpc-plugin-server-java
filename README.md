## Build docker image locally
```
docker build -t mmgrpcserver-springboot .
```
To validate if a multiarch image can be built and then use the current platform image locally, try this:
```
docker buildx create --name mybuilder --use
docker buildx build -t mmgrpcserver-springboot --platform linux/arm64/v8,linux/amd64 .
docker buildx build -t mmgrpcserver-springboot --load .
docker buildx rm mybuilder
```


## To run the dependencies only
Copy .env.local to a new file called .env and get credentials from LassPass.
```
docker-compose -f docker-compose-dependencies.yaml up
```

## To run docker image version locally with fluentd driver and Auth interceptor disabled
```
docker run --rm --log-driver=fluentd --log-opt tag=docker --log-opt fluentd-address=localhost:24225 --name  mmgrpcserver-springboot \
-p6565:6565 -p8080:8080 \
-eJAVA_OPTS='-javaagent:aws-opentelemetry-agent.jar -Djustice.grpc.interceptor.auth.enabled=false' \
-eOTEL_EXPORTER_ZIPKIN_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
-eOTEL_TRACES_EXPORTER=zipkin \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
mmgrpcserver-springboot

```

## To run docker image version locally with loki driver and Auth interceptor disabled

Need to install loki plugin first: ```docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions```
```
docker run --rm --log-driver=loki \
--log-opt loki-url="https://${LOKI_USERNAME}:${LOKI_PASSWORD}@logs-prod3.grafana.net/loki/api/v1/push" \
--log-opt loki-retries=5 \
--log-opt loki-batch-size=400 \
--name  mmgrpcserver-springboot \
-p6565:6565 -p8080:8080 \
-eJAVA_OPTS='-javaagent:aws-opentelemetry-agent.jar -Djustice.grpc.interceptor.auth.enabled=false' \
-eOTEL_EXPORTER_ZIPKIN_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
-eOTEL_TRACES_EXPORTER=zipkin \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
mmgrpcserver-springboot

```


## To run the app with with fluent driver its dependencies in one docker-compose command
```
docker-compose -f docker-compose-dependencies.yaml -f docker-compose-app.yaml up
```