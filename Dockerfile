FROM azul/zulu-openjdk:18.0.2-18.32.11
WORKDIR /opt
RUN apt-get update && \
    apt-get install -y curl
RUN curl -L https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v1.16.0/aws-opentelemetry-agent.jar  --output aws-opentelemetry-agent.jar
COPY target/*.jar /opt/app.jar
# Add "-javaagent:aws-opentelemetry-agent.jar" to $JAVA_OPTS to run with opentelementry java-agent
EXPOSE 8080
EXPOSE 6565
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
