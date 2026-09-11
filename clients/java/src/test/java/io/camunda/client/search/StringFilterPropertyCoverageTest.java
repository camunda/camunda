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
package io.camunda.client.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.impl.search.filter.StringFilterPropertyModule;
import io.camunda.client.protocol.rest.BasicStringFilterProperty;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Guards the invariant that keeps 8.10 clients compatible with pre-8.10 clusters: every generated
 * request field typed as {@code StringFilterProperty} or {@code BasicStringFilterProperty} must
 * serialize a pure exact match ({@code $eq} only) as a bare string, not as {@code {"$eq": ...}}.
 * The bare-string form is the one older clusters accept on fields that were a plain {@code string}
 * before advanced filtering was added.
 *
 * <p>The test reflects over the whole generated protocol package so a newly added field of either
 * type is covered automatically. If a future change reintroduces the collapsed object-only wire
 * form (for example by bypassing {@link StringFilterPropertyModule}), this test fails and names the
 * field.
 */
public class StringFilterPropertyCoverageTest {

  private static final String PROTOCOL_PACKAGE = "io.camunda.client.protocol.rest";
  private static final String EXACT_MATCH_VALUE = "exact-match-value";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new StringFilterPropertyModule());

  @Test
  void shouldFindStringFilterFieldsToGuard() throws Exception {
    assertThat(stringFilterFields())
        .withFailMessage(
            "Expected to discover generated StringFilterProperty/BasicStringFilterProperty fields; "
                + "found none. Has the protocol package moved?")
        .isNotEmpty();
  }

  @TestFactory
  Stream<DynamicTest> shouldSerializeEveryExactMatchStringFilterAsBareString() throws Exception {
    return stringFilterFields().stream()
        .map(
            field ->
                DynamicTest.dynamicTest(
                    field.getDeclaringClass().getSimpleName() + "." + field.getName(),
                    () -> assertBareStringExactMatch(field)));
  }

  private void assertBareStringExactMatch(final Field field) throws Exception {
    // given a container with only this filter field set to a pure exact match
    final Object container = field.getDeclaringClass().getDeclaredConstructor().newInstance();
    final Object exactMatch = exactMatchInstance(field.getType());
    field.setAccessible(true);
    field.set(container, exactMatch);

    // when serialized with the client's Jackson module
    final JsonNode tree = MAPPER.readTree(MAPPER.writeValueAsString(container));
    final JsonNode value = tree.get(jsonPropertyName(field));

    // then the field is written as a bare string, the pre-8.10-compatible form
    assertThat(value)
        .withFailMessage(
            "%s.%s produced no JSON output for an exact match",
            field.getDeclaringClass().getSimpleName(), field.getName())
        .isNotNull();
    assertThat(value.isTextual())
        .withFailMessage(
            "%s.%s must serialize an exact match as a bare string for pre-8.10 compatibility, "
                + "but was: %s",
            field.getDeclaringClass().getSimpleName(), field.getName(), value)
        .isTrue();
    assertThat(value.asText()).isEqualTo(EXACT_MATCH_VALUE);
  }

  private static Object exactMatchInstance(final Class<?> type) throws Exception {
    final Object instance = type.getDeclaredConstructor().newInstance();
    type.getMethod("set$Eq", String.class).invoke(instance, EXACT_MATCH_VALUE);
    return instance;
  }

  private static String jsonPropertyName(final Field field) {
    final JsonProperty annotation = field.getAnnotation(JsonProperty.class);
    if (annotation != null && !annotation.value().isEmpty()) {
      return annotation.value();
    }
    return field.getName();
  }

  private static List<Field> stringFilterFields() throws Exception {
    final List<Field> fields = new ArrayList<>();
    for (final Class<?> clazz : protocolClasses()) {
      if (clazz.isEnum()
          || clazz.isInterface()
          || Modifier.isAbstract(clazz.getModifiers())
          || !hasNoArgConstructor(clazz)) {
        continue;
      }
      for (final Field field : clazz.getDeclaredFields()) {
        if (field.getType() == StringFilterProperty.class
            || field.getType() == BasicStringFilterProperty.class) {
          fields.add(field);
        }
      }
    }
    return fields;
  }

  private static boolean hasNoArgConstructor(final Class<?> clazz) {
    try {
      clazz.getDeclaredConstructor();
      return true;
    } catch (final NoSuchMethodException e) {
      return false;
    }
  }

  private static List<Class<?>> protocolClasses() throws URISyntaxException {
    final Path packageDir =
        Paths.get(
                StringFilterProperty.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .resolve(PROTOCOL_PACKAGE.replace('.', File.separatorChar));
    try (Stream<Path> files = Files.walk(packageDir, 1)) {
      return files
          .filter(p -> p.toString().endsWith(".class"))
          .map(p -> p.getFileName().toString())
          .filter(name -> !name.contains("$"))
          .map(name -> name.substring(0, name.length() - ".class".length()))
          .map(StringFilterPropertyCoverageTest::loadProtocolClass)
          .collect(Collectors.toList());
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to scan protocol package " + packageDir, e);
    }
  }

  private static Class<?> loadProtocolClass(final String simpleName) {
    try {
      return Class.forName(PROTOCOL_PACKAGE + "." + simpleName);
    } catch (final ClassNotFoundException e) {
      throw new IllegalStateException("Failed to load protocol class " + simpleName, e);
    }
  }
}
