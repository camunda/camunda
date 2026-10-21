/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Guards the REST API guideline (§2.16 of {@code docs/rest-api-endpoint-guidelines.md}) that every
 * operation in the OpenAPI spec must be backed by a controller method. Without this check, an
 * operation can be documented (and picked up by SDK generators) while no endpoint serves it.
 *
 * <p>Controllers are discovered by statically scanning for {@link CamundaRestController} and
 * reading their {@link RequestMapping} meta-annotations rather than by booting a Spring context:
 * several controllers are {@code @Conditional*}/{@code @Profile}-gated, so the mappings of a
 * context depend on its configuration, whereas the guideline asks whether a controller exists in
 * code.
 *
 * <p>The spec is loaded through {@link OpenApiYamlLoader#customizeOpenApiFromYaml}, the same code
 * path used at runtime, so the paths carry the same {@code /v2} prefixing as the served API. The
 * spec file is read from the repository tree rather than the classpath because relative {@code
 * $ref}s between the per-domain YAML files do not resolve reliably from the packaged jar.
 *
 * <p>Both sides are reduced to a normalized {@link Endpoint}; path variable names are ignored as
 * only the existence of an endpoint is checked. The reverse direction (every controller endpoint
 * has a spec operation unless {@code @Hidden}) can be added by diffing {@link
 * #controllerEndpoints()} against {@link #specEndpoints()} the other way around.
 */
final class SpecControllerAlignmentTest {

  private static final String SPEC_RELATIVE_PATH =
      "zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml";
  private static final String CONTROLLER_BASE_PACKAGE = "io.camunda.zeebe.gateway.rest";
  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*}");
  private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");

  @Test
  void shouldBackEverySpecOperationWithAControllerMethod() {
    // given
    final Map<Endpoint, String> specEndpoints = specEndpoints();
    final Set<Endpoint> controllerEndpoints = controllerEndpoints();

    // when
    final List<String> missing =
        specEndpoints.entrySet().stream()
            .filter(entry -> !controllerEndpoints.contains(entry.getKey()))
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Endpoint::path)))
            .map(
                entry ->
                    "%s (%s %s)"
                        .formatted(
                            entry.getValue(), entry.getKey().method(), entry.getKey().path()))
            .toList();

    // then
    assertThat(specEndpoints).as("operations found in the spec").isNotEmpty();
    assertThat(controllerEndpoints).as("endpoints found on controllers").isNotEmpty();
    assertThat(missing)
        .as(
            "Spec operations without a matching controller method:%n%s%n%n"
                + "Every spec operation must be backed by a controller method"
                + " (docs/rest-api-endpoint-guidelines.md §2.16). Add the controller method,"
                + " or remove the operation from the spec.",
            String.join(System.lineSeparator(), missing))
        .isEmpty();
  }

  /** Maps every normalized spec endpoint to the operationId of its operation. */
  private static Map<Endpoint, String> specEndpoints() {
    final OpenAPI openApi = new OpenAPI();
    OpenApiYamlLoader.customizeOpenApiFromYaml(openApi, locateSpec().toString());

    final Map<Endpoint, String> endpoints = new LinkedHashMap<>();
    openApi
        .getPaths()
        .forEach(
            (path, pathItem) ->
                pathItem
                    .readOperationsMap()
                    .forEach(
                        (method, operation) ->
                            endpoints.put(
                                new Endpoint(method.name(), normalize(path)),
                                operation.getOperationId())));
    return endpoints;
  }

  private static Path locateSpec() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      final Path candidate = current.resolve(SPEC_RELATIVE_PATH);
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "Could not locate " + SPEC_RELATIVE_PATH + " by walking up from the working directory");
  }

  /** Collects every normalized endpoint declared by a {@link CamundaRestController}. */
  private static Set<Endpoint> controllerEndpoints() {
    return productionControllers().stream()
        .flatMap(
            controller ->
                Arrays.stream(controller.getDeclaredMethods())
                    .flatMap(method -> endpointsOf(controller, method)))
        .collect(Collectors.toSet());
  }

  private static List<Class<?>> productionControllers() {
    final var controllerFilter = new AnnotationTypeFilter(CamundaRestController.class);
    final var scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          // The default implementation also evaluates the @Conditional meta-annotations on
          // CamundaRestController and would skip every controller without a configured environment.
          @Override
          protected boolean isCandidateComponent(final MetadataReader metadataReader)
              throws IOException {
            return controllerFilter.match(metadataReader, getMetadataReaderFactory());
          }
        };

    final var productionCodeSource = codeSourceOf(CamundaRestController.class);
    return scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE).stream()
        .map(candidate -> loadClass(candidate.getBeanClassName()))
        // Skip test-scope controllers (target/test-classes): they must not "back" a spec operation.
        .filter(controller -> productionCodeSource.equals(codeSourceOf(controller)))
        .collect(Collectors.toList());
  }

  private static Stream<Endpoint> endpointsOf(final Class<?> controller, final Method method) {
    final var methodMapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    if (methodMapping == null) {
      return Stream.empty();
    }
    final var classMapping =
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    return fullPaths(classMapping, methodMapping).stream()
        .flatMap(
            path ->
                Arrays.stream(methodMapping.method())
                    .map(httpMethod -> new Endpoint(httpMethod.name(), normalize(path))));
  }

  /** Every class-level path combined with every method-level path. */
  private static List<String> fullPaths(
      final RequestMapping classMapping, final RequestMapping methodMapping) {
    return pathsOrEmpty(classMapping).stream()
        .flatMap(
            basePath ->
                pathsOrEmpty(methodMapping).stream()
                    .map(path -> path.isEmpty() ? basePath : basePath + "/" + path))
        .toList();
  }

  private static List<String> pathsOrEmpty(final RequestMapping mapping) {
    if (mapping == null || mapping.path().length == 0) {
      return List.of("");
    }
    return new ArrayList<>(Arrays.asList(mapping.path()));
  }

  private static URL codeSourceOf(final Class<?> type) {
    return type.getProtectionDomain().getCodeSource().getLocation();
  }

  private static Class<?> loadClass(final String className) {
    try {
      return ClassUtils.forName(className, SpecControllerAlignmentTest.class.getClassLoader());
    } catch (final ClassNotFoundException e) {
      throw new IllegalStateException("Could not load scanned controller " + className, e);
    }
  }

  private static String normalize(final String path) {
    final String normalized = PATH_VARIABLE.matcher(path).replaceAll("{}");
    return REPEATED_SLASHES.matcher("/" + normalized).replaceAll("/");
  }

  /** An HTTP method and a normalized path, with path variable names erased to {@code {}}. */
  private record Endpoint(String method, String path) {}
}
