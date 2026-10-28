/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.api.grpc.Ssl;
import io.camunda.configuration.security.transportlayersecurity.TlsCluster;
import io.camunda.security.api.model.config.KeystoreConfiguration;
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

final class CredentialFieldMarkingTest {

  private static final Pattern CREDENTIAL_NAME =
      Pattern.compile("(?i).*(password|secret|token|key|credentials).*");

  /** Fields whose name looks like a credential, but whose value is not one. */
  private static final Map<Class<?>, Set<String>> NOT_CREDENTIALS =
      Map.of(
          // the id of the secret, not its value
          Secrets.AwsSecretsManagerStore.class, Set.of("containerSecretId"),
          Secrets.GcpSecretManagerStore.class, Set.of("containerSecretId"),
          Secrets.CloudInternalConfig.class, Set.of("containerSecretId"),
          // the name of a key in the keystore
          KeystoreConfiguration.class, Set.of("keyAlias"),
          // an algorithm and an endpoint
          OidcConfiguration.class, Set.of("idTokenAlgorithm", "tokenUri"));

  /** Fields that hold a path to a file, which the guard has no reason to reject. */
  private static final Map<Class<?>, Set<String>> FILE_PATHS =
      Map.of(
          Ssl.class, Set.of("certificatePrivateKey"),
          TlsCluster.class, Set.of("certificatePrivateKeyPath"));

  @Test
  void shouldMarkEveryConfigurationFieldNamedLikeACredential() {
    // given
    final var fields = reachableFields(Camunda.class);

    // when
    final var unmarked =
        fields.stream()
            .filter(field -> CREDENTIAL_NAME.matcher(field.getName()).matches())
            .filter(CredentialFieldMarkingTest::holdsText)
            .filter(field -> !PhysicalTenantCredentialGuard.isCredential(field))
            .filter(field -> !listed(NOT_CREDENTIALS, field) && !listed(FILE_PATHS, field))
            .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
            .toList();

    // then
    assertThat(unmarked)
        .describedAs(
            "Fields named like a credential must be marked @Credential, listed in"
                + " PhysicalTenantCredentialGuard.CREDENTIALS_OF_OTHER_MODULES, or, if they hold"
                + " no credential, listed in this test")
        .isEmpty();
  }

  @Test
  void shouldFindTheKnownCredentialFields() {
    // given
    final var fields = reachableFields(Camunda.class);

    // when
    final var credentials =
        fields.stream()
            .filter(PhysicalTenantCredentialGuard::isCredential)
            .map(field -> field.getDeclaringClass().getSimpleName() + "#" + field.getName())
            .toList();

    // then
    assertThat(credentials)
        .contains(
            "SecondaryStorageDatabase#password",
            "OidcConfiguration#clientSecret",
            "License#key",
            "SasToken#value");
  }

  private static boolean holdsText(final Field field) {
    final var type = field.getGenericType();
    if (type == String.class || type == char[].class) {
      return true;
    }
    return type instanceof final ParameterizedType parameterized
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
