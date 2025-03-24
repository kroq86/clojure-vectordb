FROM clojure:openjdk-17-tools-deps-alpine

WORKDIR /app

COPY project.clj /app/
COPY src /app/src
COPY resources /app/resources

RUN lein deps

RUN lein uberjar

EXPOSE 3000

RUN mkdir -p /app/data

ENV PORT=3000
ENV VECTOR_DB_PATH=/app/data/vectors.db

CMD ["java", "-jar", "/app/target/uberjar/vectordb-0.1.0-SNAPSHOT-standalone.jar"] 