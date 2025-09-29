# Build stage
FROM maven:3.8.7-openjdk-18 AS build

WORKDIR /build

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src src

RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim
ARG APP_VERSION=1.0.0

ENV JAR_VERSION=${APP_VERSION}
ENV JAVA_OPTS="-Djava.awt.headless=true"

# Install required font libraries
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig \
    libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /build/target/financial-advisor-backend-*.jar /app/

EXPOSE 8085

# Specify the command to run your application
CMD java -jar financial-advisor-backend-${JAR_VERSION}.jar
