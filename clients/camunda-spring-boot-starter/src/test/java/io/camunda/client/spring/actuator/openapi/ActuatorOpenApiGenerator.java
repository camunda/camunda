/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.spring.actuator.openapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Generates the OpenAPI document for the actuator endpoints this starter contributes (currently
 * {@code JobWorkerController}, id {@code jobworkers}) and writes it under {@code
 * <outputDirectory>/openapi/actuator-openapi.yaml}, so it ships inside the packaged jar.
 *
 * <p>Invoked via {@code exec-maven-plugin} bound to the {@code prepare-package} phase (see this
 * module's pom.xml), running before {@code jar:jar} so the generated file lands in {@code
 * target/classes} in time to be bundled. Runs on every {@code package}/{@code install}/{@code
 * deploy}, including release builds that pass {@code -DskipTests=true} — unlike a JUnit test bound
 * to the {@code test} phase, this is not skipped by that flag.
 */
public final class ActuatorOpenApiGenerator {

  private ActuatorOpenApiGenerator() {}

  public static void main(final String[] args) throws IOException, InterruptedException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected exactly one argument: the output directory");
    }

    final Path outputPath = Path.of(args[0], "openapi", "actuator-openapi.yaml");

    final ConfigurableApplicationContext context =
        new SpringApplicationBuilder(OpenApiGenerationApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties(
                "server.port=0",
                "management.endpoints.web.exposure.include=*",
                "springdoc.show-actuator=true",
                "spring.main.banner-mode=off")
            .run(args);
    try {
      final Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
      final HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs.yaml"))
              .GET()
              .build();
      final HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
      final String spec = response.body();

      if (response.statusCode() != 200 || spec == null || spec.isBlank()) {
        throw new IllegalStateException(
            "Failed to generate actuator OpenAPI spec, status=" + response.statusCode());
      }
      if (!spec.contains("/actuator/jobworkers")) {
        throw new IllegalStateException(
            "Generated actuator OpenAPI spec is missing the expected jobworkers endpoint");
      }

      Files.createDirectories(outputPath.getParent());
      Files.writeString(outputPath, spec, StandardCharsets.UTF_8);
    } finally {
      context.close();
    }
  }
}
