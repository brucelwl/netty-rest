FROM openjdk:21
VOLUME /Users/admin/disk/tmpdir

ADD ./target/rest-server-boot-starter-example-0.0.1.jar rest-server-boot-starter-example.jar

ENTRYPOINT ["sh", "-c", "java -jar rest-server-boot-starter-example.jar"]

