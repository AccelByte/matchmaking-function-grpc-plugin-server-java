## Build docker image locally
```
docker build -t mmgrpcserver-springboot .
```

## Run docker image version locally
```
docker run -p6565:6565 -p8080:8080 \
-eJAVA_OPTS=-javaagent:aws-opentelemetry-agent.jar \
-eOTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4317/ \
-eOTEL_METRICS_EXPORTER=none \
-eOTEL_SERVICE_NAME=CustomMatchMakingFunctionJavaDocker \
-eOTEL_PROPAGATORS=b3multi \
mmgrpcserver-springboot
```