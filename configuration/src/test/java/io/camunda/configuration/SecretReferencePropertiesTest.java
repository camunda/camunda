/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.data.secondarystorage.SecondaryStorageDatabase;
import io.camunda.security.api.model.config.KeystoreConfiguration;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class SecretReferencePropertiesTest {

  private static final Pattern CREDENTIAL_NAME =
      Pattern.compile("(?i).*(password|secret|token|key|credential|connectionstring).*");

  /** Fields named like a credential that don't hold one. */
  private static final Map<Class<?>, Set<String>> NOT_CREDENTIALS =
      Map.of(
          Secrets.AwsSecretsManagerStore.class, Set.of("containerSecretId"),
          Secrets.AzureKeyVaultStore.class, Set.of("containerSecretId"),
          Secrets.GcpSecretManagerStore.class, Set.of("containerSecretId"),
          KeystoreConfiguration.class, Set.of("keyAlias"),
          OidcConfiguration.class, Set.of("idTokenAlgorithm", "tokenUri"));

  @Test
  void shouldMarkEveryFieldNamedLikeACredential() {
    // given
    final var fields = reachableFields(Camunda.class);

    // when
    final var unmarked =
        fields.stream()
            .filter(field -> CREDENTIAL_NAME.matcher(field.getName()).matches())
            .filter(SecretReferencePropertiesTest::holdsText)
            .filter(field -> !SecretReferenceProperties.isCredential(field))
            .filter(field -> !listed(NOT_CREDENTIALS, field))
            .map(SecretReferencePropertiesTest::name)
            .toList();

    // then
    assertThat(unmarked)
        .describedAs(
            "A field named like a credential must be marked with @AcceptsSecretReference, or be"
                + " listed in this test if it doesn't hold one")
        .isEmpty();
  }

  @Test
  void shouldAcceptAReferenceForAUsernameWithoutTreatingItAsACredential() throws Exception {
    // given
    final var username = SecondaryStorageDatabase.class.getDeclaredField("username");

    // when
    final var acceptsReference = SecretReferenceProperties.acceptsReference(username);
    final var credential = SecretReferenceProperties.isCredential(username);

    // then
    assertThat(acceptsReference).isTrue();
    assertThat(credential).isFalse();
  }

  @Test
  void shouldAcceptAReferenceForAListedPropertyOfAnotherModule() throws Exception {
    // given
    final var password = ConfiguredUser.class.getDeclaredField("password");

    // when
    final var acceptsReference = SecretReferenceProperties.acceptsReference(password);
    final var credential = SecretReferenceProperties.isCredential(password);

    // then
    assertThat(acceptsReference).isTrue();
    assertThat(credential).isTrue();
  }

  @Test
  void shouldNotAcceptAReferenceForTheOidcClientSecretYet() throws Exception {
    // given
    final var clientSecret = OidcConfiguration.class.getDeclaredField("clientSecret");

    // when
    final var acceptsReference = SecretReferenceProperties.acceptsReference(clientSecret);
    final var credential = SecretReferenceProperties.isCredential(clientSecret);

    // then
    assertThat(acceptsReference).isFalse();
    assertThat(credential).isTrue();
  }

  private static String name(final Field field) {
    return field.getDeclaringClass().getName() + "#" + field.getName();
  }

  private static boolean holdsText(final Field field) {
    final var type = field.getGenericType();
    return type == String.class
        || type instanceof final ParameterizedType parameterized
            && parameterized.getRawType() == Optional.class
            && parameterized.getActualTypeArguments()[0] == String.class;
  }

  private static boolean listed(final Map<Class<?>, Set<String>> fields, final Field field) {
    return fields.getOrDefault(field.getDeclaringClass(), Set.of()).contains(field.getName());
  }

  private static List<Field> reachableFields(final Class<?> root) {
    final List<Field> fields = new ArrayList<>();
    collect(root, fields, new HashSet<>());
    return fields;
  }

  private static void collect(
      final Class<?> type, final List<Field> fields, final Set<Class<?>> visited) {
    if (!visited.add(type)) {
      return;
    }
    for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
      for (final var field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }
        fields.add(field);
        for (final var referenced : classesOf(field.getGenericType())) {
          if (referenced.getName().startsWith("io.camunda.") && !referenced.isEnum()) {
            collect(referenced, fields, visited);
          }
        }
      }
    }
  }

  private static List<Class<?>> classesOf(final Type type) {
    final List<Class<?>> classes = new ArrayList<>();
    if (type instanceof final Class<?> clazz) {
      classes.add(clazz);
    } else if (type instanceof final ParameterizedType parameterized) {
      classes.addAll(classesOf(parameterized.getRawType()));
      for (final var argument : parameterized.getActualTypeArguments()) {
        classes.addAll(classesOf(argument));
      }
    }
    return classes;
  }
}
