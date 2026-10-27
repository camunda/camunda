/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.multipartition;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoIncidentWasRaised;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitActivationExported;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstances;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithEmptySecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.pollUntilActivated;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionRequestPartitions;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.writeSecret;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers a secret used by jobs spread over several partitions. Each partition keeps its own pending
 * references and runs its own background resolution, while the store and the values it holds belong
 * to the broker and are shared by all of them, so a job is served whether its partition resolved
 * the reference itself or another one had already done so.
 *
 * <p>An engine test runs a single partition, so neither the split state nor the shared store is
 * exercised there.
 */
@ZeebeIntegration
final class SecretResolutionMultiPartitionIT {

  private static final int PARTITION_COUNT = 3;

  /** A multiple of the partition count, so the gateway's round robin reaches every partition. */
  private static final int INSTANCES = 12;

  @TestZeebe(initMethod = "initTestStandaloneBroker", partitionCount = PARTITION_COUNT)
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String secretName = uniqueSecretName();
  private final String secretValue = "value-of-" + secretName;
  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    // the annotation's partition count is what the topology is awaited against, the cluster
    // configuration is what the broker is started with, so both are needed
    broker =
        newBrokerWithEmptySecretStore()
            .withClusterConfig(cluster -> cluster.setPartitionCount(PARTITION_COUNT));
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldServeJobsOfEveryPartitionWaitingOnTheSameSecret() {
    // given
    writeSecret(broker, secretName, secretValue);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    createProcessInstances(client, processId, INSTANCES);

    // when
    final Map<Long, ActivatedJob> activated = pollUntilActivated(client, jobType, INSTANCES);

    // then - every job is served with the value, wherever it was created
    assertThat(activated.values())
        .allSatisfy(
            job -> assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue));
    final List<Integer> partitionsOfActivatedJobs =
        activated.keySet().stream().map(Protocol::decodePartitionId).distinct().toList();
    assertThat(partitionsOfActivatedJobs)
        .as("the jobs are spread over every partition")
        .hasSize(PARTITION_COUNT);

    // and - the reference was resolved by a partition rather than served from thin air, and no
    // partition ended up with an incident. Which partitions requested a resolution is not fixed:
    // the values live on the broker, so a partition collecting its jobs after another one resolved
    // the reference finds the value already there and never parks anything
    activated.keySet().forEach(jobKey -> awaitActivationExported(jobType, jobKey));
    assertThat(resolutionRequestPartitions(secretName)).isNotEmpty();
    assertNoIncidentWasRaised();
    assertNoRecordCarriesValue(secretValue);
  }
}
