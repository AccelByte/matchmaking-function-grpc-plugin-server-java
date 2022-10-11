FROM azul/zulu-openjdk:17.0.4-17.36.13
WORKDIR /opt
COPY jars/aws-opentelemetry-agent.jar /opt/aws-opentelemetry-agent.jar
COPY target/*.jar /opt/app.jar
# Add "-javaagent:aws-opentelemetry-agent.jar" to $JAVA_OPTS to run with opentelementry java-agent
EXPOSE 8080
EXPOSE 6565
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
