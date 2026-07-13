# --- build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnwはWindowsチェックアウト時にCRLF化されることがあるため正規化する
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

# --- run stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system fests && useradd --system --gid fests fests
COPY --from=build /app/target/*.jar app.jar
RUN chown -R fests:fests /app
USER fests

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
