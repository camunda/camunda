/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.apache.hc.core5.http.HttpStatus;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public final class CreateAgentInstanceTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  public void shouldReturnUnavailableWhenElementInstanceKeyEncodesNonExistentPartition() {
    // given
    // Keys encode the target partition in their upper 13 bits; a key for a non-existent partition
    // yields 503 UNAVAILABLE (PartitionNotFoundException).
    final long elementInstanceKey = Protocol.encodePartitionId(999, 1);

    // when
    final var responseFuture =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("test-model")
            .provider("test-provider")
            .systemPrompt("You are a helpful assistant.")
            .send();

    // then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: UNAVAILABLE")
        .hasMessageContaining("status: 503")
        .hasMessageContaining("Expected to handle request, but request could not be delivered");
  }

  @Test
  public void shouldReturnNotFoundWhenElementInstanceDoesNotExistOnExistingPartition() {
    // given
    final int partition = resourcesHelper.getPartitions().getFirst();
    final long nonExistingKey = Protocol.encodePartitionId(partition, 2);

    // when
    final var responseFuture =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(nonExistingKey)
            .model("test-model")
            .provider("test-provider")
            .systemPrompt("You are a helpful assistant.")
            .send();

    // then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: NOT_FOUND")
        .hasMessageContaining("status: 404")
        .hasMessageContaining(
            "Expected to create agent instance for element instance with key '%d', but no such element instance was found."
                .formatted(nonExistingKey));
  }

  @Test
  public void shouldReturn409ConflictWhenAgentInstanceAlreadyExistsForElementInstance() {
    // given - a service task that stays active until a job is completed, giving us a stable
    // elementInstanceKey to use for both CREATE attempts.
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("conflict-test-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("agent-conflict-job"))
                .endEvent()
                .done());
    resourcesHelper.createProcessInstance(processDefinitionKey);
    final long elementInstanceKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType("agent-conflict-job")
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // first CREATE — must succeed and return the new agentInstanceKey.
    final var firstResult =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("test-model")
            .provider("test-provider")
            .systemPrompt("You are a helpful assistant.")
            .execute();

    // when - second CREATE for the same element instance (different payload to rule out
    // accidental pass-through).
    final var exception =
        AssertionsForClassTypes.assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () ->
                    client
                        .newCreateAgentInstanceCommand()
                        .elementInstanceKey(elementInstanceKey)
                        .model("different-model")
                        .provider("test-provider")
                        .systemPrompt("A different system prompt.")
                        .execute())
            .actual();

    // then - 409 Conflict with ALREADY_EXISTS and the full detail message embedding the
    // existing agentInstanceKey.
    assertThat(exception.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
    assertThat(exception.details().getTitle()).isEqualTo("ALREADY_EXISTS");
    assertThat(exception.details().getDetail())
        .startsWith("Command 'CREATE' rejected with code 'ALREADY_EXISTS':")
        .endsWith(
            "Expected to associate element instance with key '%d' with an agent instance, but it is already associated with agent instance with key '%d'."
                .formatted(elementInstanceKey, firstResult.getAgentInstanceKey()));
  }
}
