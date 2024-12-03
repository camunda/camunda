/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import java.time.Duration;
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
 * ZeebeClient zeebeClient = ...; // initialize your Zeebe client
 *
 * // Deploy a resource
 * DeploymentEvent deploymentEvent = deployResource(zeebeClient, "path/to/resource.bpmn");
 *
 * // Start a process instance
 * ProcessInstanceEvent processInstanceEvent = startProcessInstance(zeebeClient, "processId");
 *
 * // Wait for process instances to start
 * waitForProcessInstancesToStart(zeebeClient, 1);
 *
 * // Assert sorting of query results
 * SearchQueryResponse<Incident> resultAsc = ...; // get ascending sorted result
 * SearchQueryResponse<Incident> resultDesc = ...; // get descending sorted result
 * assertSorted(resultAsc, resultDesc, Incident::getCreationTime);
 * }</pre>
 */
public class QueryTest {

  public static DeploymentEvent deployResource(
      final ZeebeClient zeebeClient, final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  public static ProcessInstanceEvent startProcessInstance(
      final ZeebeClient zeebeClient, final String bpmnProcessId) {
    return startProcessInstance(zeebeClient, bpmnProcessId, null);
  }

  public static ProcessInstanceEvent startProcessInstance(
      final ZeebeClient zeebeClient, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send().join();
  }

  public static void waitForProcessInstancesToStart(
      final ZeebeClient zeebeClient, final int expectedProcessInstances) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newProcessInstanceQuery().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessInstances);
            });
  }

  public static void waitForFlowNodeInstances(
      final ZeebeClient zeebeClient, final int expectedFlowNodeInstances) {
    Awaitility.await("should wait until flow node instances are available")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newFlownodeInstanceQuery().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedFlowNodeInstances);
            });
  }

  public static void waitForProcessesToBeDeployed(
      final ZeebeClient zeebeClient, final int expectedProcessDefinitions) {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newProcessDefinitionQuery().send().join();
              assertThat(result.page().totalItems()).isEqualTo(expectedProcessDefinitions);
            });
  }

  public static void waitUntilProcessInstanceHasIncidents(
      final ZeebeClient zeebeClient, final int expectedIncidents) {
    Awaitility.await("should wait until incidents are exists")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  zeebeClient
                      .newProcessInstanceQuery()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static void waitUntilFlowNodeInstanceHasIncidents(
      final ZeebeClient zeebeClient, final int expectedIncidents) {
    Awaitility.await("should wait until flow node instance has incidents")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  zeebeClient
                      .newFlownodeInstanceQuery()
                      .filter(f -> f.hasIncident(true))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(expectedIncidents);
            });
  }

  public static <T, U extends Comparable<U>> void assertSorted(
      final SearchQueryResponse<T> resultAsc,
      final SearchQueryResponse<T> resultDesc,
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
