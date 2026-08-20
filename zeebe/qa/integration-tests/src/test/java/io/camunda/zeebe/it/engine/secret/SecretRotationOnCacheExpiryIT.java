/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.activateOneJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitActivationExported;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithEmptySecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionWasRequestedFor;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.writeSecret;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers what a worker receives after the secret it uses is rotated in the store: the cached value
 * until it expires, and the rotated one afterwards.
 *
 * <p>Only reachable here. The cache's expiry is driven by the broker's actor clock, which is what
 * lets {@code /actuator/clock} reach it, and neither the clock service nor the configured ttl
 * exists in an engine test. The ttl also cannot go below a minute, so the test moves the clock
 * rather than waiting.
 */
@ZeebeIntegration
final class SecretRotationOnCacheExpiryIT {

  private static final Duration CACHE_TTL = Duration.ofMinutes(1);

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String secretName = uniqueSecretName();
  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker =
        newBrokerWithEmptySecretStore()
            .withUnifiedConfig(config -> config.getSecrets().getCache().setTtl(CACHE_TTL))
            .withProperty("zeebe.clock.controlled", true);
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldServeTheRotatedSecretOnceTheCachedValueExpires() {
    // given - a first activation that read the secret into the cache
    final String firstValue = "first-" + secretName;
    final String rotatedValue = "rotated-" + secretName;
    writeSecret(broker, secretName, firstValue);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    createProcessInstance(client, processId);
    assertThat(activateOneJob(client, jobType).getVariablesAsMap())
        .containsEntry(INPUT_TARGET, firstValue);

    // when - the secret is rotated in the store while the cached value is still within its ttl
    writeSecret(broker, secretName, rotatedValue);
    final long cachedInstanceKey = createProcessInstance(client, processId);
    final long cachedJobKey = awaitJobKeyOf(cachedInstanceKey);

    // then - the activation keeps serving the cached value without going through the store, since
    // nothing reads it for a reference whose value is held
    final ActivatedJob cachedJob = activateOneJob(client, jobType);
    assertThat(cachedJob.getKey()).isEqualTo(cachedJobKey);
    assertThat(cachedJob.getVariablesAsMap()).containsEntry(INPUT_TARGET, firstValue);
    awaitActivationExported(jobType, cachedJobKey);
    assertThat(resolutionWasRequestedFor(secretName, cachedJobKey)).isFalse();

    // when - time moves past the ttl of the cached value
    ActorClockActuator.of(broker).addTime(new AddTimeRequest(CACHE_TTL.toMillis() * 2));
    final long rotatedInstanceKey = createProcessInstance(client, processId);
    final long rotatedJobKey = awaitJobKeyOf(rotatedInstanceKey);

    // then - the job is parked and its reference resolved again, which serves the rotated value
    final ActivatedJob rotatedJob = activateOneJob(client, jobType);
    assertThat(rotatedJob.getKey()).isEqualTo(rotatedJobKey);
    assertThat(rotatedJob.getVariablesAsMap()).containsEntry(INPUT_TARGET, rotatedValue);
    awaitActivationExported(jobType, rotatedJobKey);
    assertThat(resolutionWasRequestedFor(secretName, rotatedJobKey))
        .as("the expired value was resolved again rather than served from the cache")
        .isTrue();
    assertNoRecordCarriesValue(firstValue);
    assertNoRecordCarriesValue(rotatedValue);
  }
}
