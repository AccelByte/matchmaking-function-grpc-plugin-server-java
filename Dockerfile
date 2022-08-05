FROM azul/zulu-openjdk:18.0.2-18.32.11
WORKDIR /opt
EXPOSE 8080
EXPOSE 6565
COPY target/*.jar /opt/app.jar
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
