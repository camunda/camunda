/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.smoke;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.activateOneJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.backtickedSecretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.util.ZeebeContainerUtil.newClientBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers values from the environment-variable secret store that a simple string does not: a
 * non-ASCII value, a multi-line PEM, a JSON document and a name only a backticked reference can
 * reach, each set in the container's environment and passed through a deployed process into the job
 * a worker receives.
 *
 * <p>Only a real process environment shows these: how the JVM decodes its environment depends on
 * the image's locale, and a test JVM can't set a multi-line value or a dashed name on itself.
 */
@Testcontainers
final class EnvSecretStoreContainerSmokeIT {

  private static final String PREFIX = "SMOKE_";
  private static final String NON_ASCII = "pässwörd€";
  private static final String PEM =
      """
      -----BEGIN CERTIFICATE-----
      bm90IGEgcmVhbCBjZXJ0aWZpY2F0ZSwg
      b25seSB0ZXN0IGRhdGE=
      -----END CERTIFICATE-----
      """;
  private static final String JSON = "{\"user\":\"svc\",\"port\":5432,\"tags\":[\"a\",\"b\"]}";
  private static final String DB_PASSWORD = "db-password-value";

  @Container
  private static final BrokerContainer BROKER =
      new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withUnifiedConfig(
              cfg -> cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none))
          .withEnv("CAMUNDA_SECRETS_STORES_ENV_DEFAULT_PREFIX", PREFIX)
          .withEnv(PREFIX + "PASSWORD", NON_ASCII)
          .withEnv(PREFIX + "TLS_KEY", PEM)
          .withEnv(PREFIX + "CONFIG", JSON)
          .withEnv(PREFIX + "db-password", DB_PASSWORD);

  @AutoClose private CamundaClient client;

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeEach
  void beforeEach() {
    // container tests might be less responsive when emulation is involved, e.g. of ARM64
    client =
        newClientBuilder(BROKER)
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofMinutes(2))
            .build();
  }

  @ContainerSmokeTest
  void shouldHandANonAsciiEnvironmentValueToAJobUnchanged() {
    // given
    startProcessWithInput(secretReference("PASSWORD"));

    // when
    final Object value = inputOfActivatedJob();

    // then
    assertThat(value).isEqualTo(NON_ASCII);
  }

  @ContainerSmokeTest
  void shouldHandOutAMultiLinePemByteForByte() {
    // given
    startProcessWithInput(secretReference("TLS_KEY"));

    // when
    final Object value = inputOfActivatedJob();

    // then - newlines and the trailing newline included
    assertThat(value).isEqualTo(PEM);
  }

  @ContainerSmokeTest
  void shouldHandOutAJsonValueAsTheSameStringUnparsed() {
    // given
    startProcessWithInput(secretReference("CONFIG"));

    // when
    final Object value = inputOfActivatedJob();

    // then - a string, not the object it describes
    assertThat(value).isEqualTo(JSON);
  }

  @ContainerSmokeTest
  void shouldResolveABacktickedReferenceToItsPrefixedVariable() {
    // given
    startProcessWithInput(backtickedSecretReference("db-password"));

    // when
    final Object value = inputOfActivatedJob();

    // then
    assertThat(value).isEqualTo(DB_PASSWORD);
  }

  private void startProcessWithInput(final String reference) {
    deployProcessWithInput(client, processId, jobType, reference);
    createProcessInstance(client, processId);
  }

  private Object inputOfActivatedJob() {
    return activateOneJob(client, jobType).getVariablesAsMap().get(INPUT_TARGET);
  }
}
