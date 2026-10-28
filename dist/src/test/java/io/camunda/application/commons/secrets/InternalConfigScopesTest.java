/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.CloudInternalConfig;
import io.camunda.configuration.Secrets.Stores;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class InternalConfigScopesTest {

  @ParameterizedTest(name = "jobs {0}/{1}, internal-config {2}/{3}")
  @CsvSource(
      nullValues = "-",
      value = {
        "-, -, -, -",
        "camunda/, -, camunda/, -",
        "camunda/, -, camunda/internal-, -",
        "camunda/, -, camunda/, internal",
        "-, -, -, internal",
        "camunda/, jobs, camunda/, jobs",
        "-, jobs, -, -",
      })
  void shouldRejectAnInternalConfigScopeThatJobsCanRead(
      final @Nullable String jobPrefix,
      final @Nullable String jobContainer,
      final @Nullable String internalPrefix,
      final @Nullable String internalContainer) {
    // given
    final var stores = aws(jobPrefix, jobContainer, internalPrefix, internalContainer);

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> InternalConfigScopes.requireSeparateScopes("tenanta", stores))
        .withMessageContaining("camunda.physical-tenants.tenanta.secrets.stores.aws.default");
  }

  @ParameterizedTest(name = "jobs {0}/{1}, internal-config {2}/{3}")
  @CsvSource(
      nullValues = "-",
      value = {
        "camunda/jobs/, -, camunda/internal/, -",
        "camunda/jobs/, -, camunda/internal/, all",
        "camunda/, -, camunda/internal/, -",
        "camunda/, jobs, camunda/, internal",
        "camunda/, jobs, camunda/internal/, -",
      })
  void shouldAcceptAnInternalConfigScopeThatJobsCannotRead(
      final @Nullable String jobPrefix,
      final @Nullable String jobContainer,
      final @Nullable String internalPrefix,
      final @Nullable String internalContainer) {
    // given
    final var stores = aws(jobPrefix, jobContainer, internalPrefix, internalContainer);

    // when / then
    assertThatCode(() -> InternalConfigScopes.requireSeparateScopes("tenanta", stores))
        .doesNotThrowAnyException();
  }

  private static Stores aws(
      final @Nullable String jobPrefix,
      final @Nullable String jobContainer,
      final @Nullable String internalPrefix,
      final @Nullable String internalContainer) {
    final var internalConfig = new CloudInternalConfig();
    internalConfig.setPathPrefix(internalPrefix);
    internalConfig.setContainerSecretId(internalContainer);
    final var store = new AwsSecretsManagerStore();
    store.setPathPrefix(jobPrefix);
    store.setContainerSecretId(jobContainer);
    store.setInternalConfig(internalConfig);
    final Map<String, AwsSecretsManagerStore> aws = new LinkedHashMap<>();
    aws.put("default", store);
    final var stores = new Stores();
    stores.setAws(aws);
    return stores;
  }
}
