/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.SearchResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.awaitility.Awaitility;

/**
 * This class provides several static methods to facilitate common operations such as deploying
 * resources, starting process instances, and waiting for certain conditions to be met. These
 * methods are designed to be used in integration tests to reduce code duplication and improve
 * readability.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CamundaClient camundaClient = ...; // initialize your Camunda client
 *
 * // Deploy a resource
 * DeploymentEvent deploymentEvent = deployResource(camundaClient, "path/to/resource.bpmn");
 *
 * // Start a process instance
 * ProcessInstanceEvent processInstanceEvent = startProcessInstance(camundaClient, "processId");
 *
 * // Wait for process instances to start
 * waitForProcessInstancesToStart(camundaClient, 1);
 *
 * // Assert sorting of query results
 * SearchQueryResponse<Incident> resultAsc = ...; // get ascending sorted result
 * SearchQueryResponse<Incident> resultDesc = ...; // get descending sorted result
 * assertSorted(resultAsc, resultDesc, Incident::getCreationTime);
 * }</pre>
 */
public final class TestHelper {

  public static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  public static DeploymentEvent deployResourceForTenant(
      final CamundaClient camundaClient, final String resourceName, final String tenantId) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenantId)
        .send()
        .join();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId) {
    return startProcessInstance(camundaClient, bpmnProcessId, null);
  }

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send().join();
  }

  public static void waitForProcessInstancesToStart(
      final CamundaClient camundaClient, final int expectedProcessInstances) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessInstanceSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
  }

  public static void waitForFlowNodeInstances(
      final CamundaClient camundaClient, final int expectedFlowNodeInstances) {
    Awaitility.await("should wait until flow node instances are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newFlownodeInstanceSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedFlowNodeInstances);
            });
  }

  public static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedProcessDefinitions) {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessDefinitions);
            });
  }

  public static void waitUntilProcessInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedIncidents) {
    Awaitility.await("should wait until incidents are exists")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static void waitUntilProcessInstanceIsEnded(
      final CamundaClient camundaClient, final long processInstanceKey) {
    Awaitility.await("should wait until process is ended")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items().getFirst().getEndDate()).isNotNull();
            });
  }

  public static void waitUntilFlowNodeInstanceHasIncidents(
      final CamundaClient camundaClient, final int expectedIncidents) {
    Awaitility.await("should wait until flow node instance has incidents")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newFlownodeInstanceSearchRequest()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static <T, U extends Comparable<U>> void assertSorted(
      final SearchResponse<T> resultAsc,
      final SearchResponse<T> resultDesc,
      final Function<T, U> propertyExtractor) {
    assertThatIsAscSorted(resultAsc.items(), propertyExtractor);
    assertThatIsDescSorted(resultDesc.items(), propertyExtractor);
  }

  public static <T, U extends Comparable<U>> void assertThatIsAscSorted(
      final List<T> items, final Function<T, U> propertyExtractor) {
    assertThatIsSortedBy(items, propertyExtractor, Comparator.naturalOrder());
  }

  public static <T, U extends Comparable<U>> void assertThatIsDescSorted(
      final List<T> items, final Function<T, U> propertyExtractor) {
    assertThatIsSortedBy(items, propertyExtractor, Comparator.reverseOrder());
  }

  public static <T, U extends Comparable<U>> void assertThatIsSortedBy(
      final List<T> items, final Function<T, U> propertyExtractor, final Comparator<U> comparator) {
    final var sorted =
        items.stream().map(propertyExtractor).filter(Objects::nonNull).sorted(comparator).toList();
    assertThat(items.stream().map(propertyExtractor).filter(Objects::nonNull).toList())
        .containsExactlyElementsOf(sorted);
  }
}
