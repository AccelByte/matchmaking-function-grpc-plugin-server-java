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

## Run docker image version locally
```
docker run --name  mmgrpcserver-springboot \
-p6565:6565 -p8080:8080 \
-eJAVA_OPTS=-javaagent:aws-opentelemetry-agent.jar \
-eOTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4317/ \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
mmgrpcserver-springboot
```