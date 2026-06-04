# Multi-stage Dockerfile para MS-Api-Gateway (Spring Cloud Gateway)
# 
# Etapa 1: Build — Compilar con Maven en contenedor
# Etapa 2: Runtime — Ejecutar JAR con JRE mínimo
#
# Notas:
# - Base image: eclipse-temurin:21-jre-alpine (Java 21 LTS oficial, pequeño)
# - El gateway se conecta a otros microservicios en localhost/host network
# - En desarrollo local: usar docker run --network host o docker-compose
# - BD PostgreSQL y Redis se asumen disponibles en host machine

# ============================================================================
# ETAPA 1: BUILD
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Instalar Maven (evita problemas con mvnw en Alpine)
RUN apk add --no-cache maven

# Copiar archivos de proyecto
COPY pom.xml .
COPY src ./src

# Compilar con Maven — descargar dependencias y empaquetar JAR
RUN mvn clean package -DskipTests

# ============================================================================
# ETAPA 2: RUNTIME
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache wget

# Copiar JAR desde etapa de build
COPY --from=builder /app/target/*.jar app.jar

# Metadatos
LABEL maintainer="CatástrofesCL <dev@catastrofescl.cl>"
LABEL description="API Gateway — CatástrofesCL: Punto de entrada único, validación Firebase, rate limiting, enrutamiento"

# Puerto expuesto
EXPOSE 8080

# Health check (verifica que el gateway esté respondiendo)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Variables de entorno con valores por defecto para desarrollo local
ENV JAVA_OPTS="-Xmx256m -Xms128m"
ENV SPRING_PROFILES_ACTIVE=local

# Punto de entrada: ejecutar JAR con JVM opts configurables
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
