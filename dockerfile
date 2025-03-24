FROM openjdk:17-slim

WORKDIR /app

# Install dependencies
RUN apt-get update && \
    apt-get install -y curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Leiningen
ENV LEIN_VERSION=2.10.0
ENV LEIN_INSTALL=/usr/local/bin/
RUN mkdir -p $LEIN_INSTALL \
  && curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/$LEIN_VERSION/bin/lein > $LEIN_INSTALL/lein \
  && chmod +x $LEIN_INSTALL/lein \
  && lein version

# Copy the project files
COPY project.clj /app/
COPY src /app/src
COPY resources /app/resources

# Install dependencies and build
RUN lein deps
RUN lein uberjar

# Expose the default port
EXPOSE 3000

# Create data directory
RUN mkdir -p /app/data

# Set environment variables
ENV PORT=3000
ENV VECTOR_DB_PATH=/app/data/vectors.db

# Run the application
CMD ["java", "-jar", "/app/target/uberjar/vectordb-0.1.0-SNAPSHOT-standalone.jar"] 