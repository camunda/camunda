/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.InternalConfig;
import io.camunda.configuration.Secrets.Stores;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** Keeps the store types of the configuration and of {@code STORE_BINDINGS} in step. */
class StoreBindingsTest {

  /** The least configuration of a store of each type. */
  private static final Map<String, Map<String, Object>> MINIMAL_CONFIGURATION =
      Map.of(
          "file", Map.of("path", "/etc/camunda/config-secrets"),
          "aws", Map.of("region", "eu-west-1"),
          "gcp", Map.of("project-id", "camunda"),
          "env", Map.of("prefix", "CONFIG_SECRET_"),
          "azure", Map.of("vault-url", "https://camunda.vault.azure.net"));

  @Test
  void shouldBindEveryJobStoreType() {
    // when
    final var types = fieldNames(Stores.class);

    // then
    assertThat(types).containsExactlyInAnyOrderElementsOf(bindingKeys().toList());
  }

  @Test
  void shouldBindEveryConfigurationSecretsStoreType() {
    // when
    final var types = fieldNames(InternalConfig.class);

    // then
    assertThat(types).containsExactlyInAnyOrderElementsOf(bindingKeys().toList());
  }

  @ParameterizedTest
  @MethodSource("bindings")
  void shouldReadTheConfigurationSecretsStoreOfEachType(
      final SecretStoreConfiguration.StoreBinding<?> binding) {
    // given
    final var properties = MINIMAL_CONFIGURATION.get(binding.propertyKey());
    assertThat(properties)
        .describedAs("minimal configuration of %s", binding.propertyKey())
        .isNotNull();
    final Map<String, Object> prefixed = new HashMap<>();
    properties.forEach(
        (key, value) ->
            prefixed.put(
                "camunda.secrets.internal-config." + binding.propertyKey() + "." + key, value));
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", prefixed));
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

    // when
    final var configured =
        SecretStoreConfiguration.STORE_BINDINGS.stream()
            .filter(
                candidate ->
                    candidate
                            .configurationSelector()
                            .apply(camunda.getSecrets().getInternalConfig())
                        != null)
            .toList();

    // then
    assertThat(configured).containsExactly(binding);
  }

  private static Stream<SecretStoreConfiguration.StoreBinding<?>> bindings() {
    return SecretStoreConfiguration.STORE_BINDINGS.stream();
  }

  private static Stream<String> bindingKeys() {
    return SecretStoreConfiguration.STORE_BINDINGS.stream()
        .map(SecretStoreConfiguration.StoreBinding::propertyKey);
  }

  private static List<String> fieldNames(final Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .map(Field::getName)
        .toList();
  }
}
