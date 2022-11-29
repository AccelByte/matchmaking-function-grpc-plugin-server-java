FROM azul/zulu-openjdk:17.0.4-17.36.13

WORKDIR /app
COPY jars/aws-opentelemetry-agent.jar aws-opentelemetry-agent.jar
COPY target/*.jar app.jar

# Plugin arch gRPC server port
EXPOSE 6565

# Prometheus /metrics web server port
EXPOSE 8080

ENTRYPOINT java -javaagent:aws-opentelemetry-agent.jar $JAVA_OPTS -jar app.jar
