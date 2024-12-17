/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.function.Consumer;

public class TestSteps<SELF extends TestSteps<SELF>> {

  protected String processDefinitionId;
  protected Long processInstanceKey;
  protected Long childProcessInstanceKey;
  protected final TestStandaloneBroker broker;
  protected final CamundaClient client;

  public TestSteps(final TestStandaloneBroker broker) {
    this.broker = broker;
    client = broker.newClientBuilder().build();
  }

  public SELF self() {
    return (SELF) this;
  }

  public SELF deployProcessFromClasspath(final String classpath) {
    final var deployment =
        client.newDeployResourceCommand().addResourceFromClasspath(classpath).send().join();
    processDefinitionId = deployment.getProcesses().getFirst().getBpmnProcessId();

    return self();
  }

  public SELF startProcessInstance() {
    return startProcessInstance(processDefinitionId);
  }

  public SELF startProcessInstance(final String processDefinitionId) {
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processDefinitionId)
            .latestVersion()
            .send()
            .join();
    processInstanceKey = processInstance.getProcessInstanceKey();

    return self();
  }

  public SELF waitForChildProcessInstance() {
    return processInstanceExistAndMatch(
        f -> f.parentProcessInstanceKey(processInstanceKey),
        list -> {
          assertThat(list).hasSize(1);
          childProcessInstanceKey = list.getFirst().getProcessInstanceKey();
        });
  }

  public SELF incidentsExistAndMatch(
      final Consumer<IncidentFilter> filter, final Consumer<List<Incident>> asserter) {
    await()
        .untilAsserted(
            () -> {
              final var incidents = client.newIncidentQuery().filter(filter).send().join().items();
              asserter.accept(incidents);
            });

    return self();
  }

  public SELF flowNodeInstanceExistAndMatch(
      final Consumer<FlownodeInstanceFilter> filter,
      final Consumer<List<FlowNodeInstance>> asserter) {
    await()
        .untilAsserted(
            () -> {
              final var result =
                  client.newFlownodeInstanceQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });

    return self();
  }

  public SELF processInstanceExistAndMatch(
      final Consumer<ProcessInstanceFilter> filter,
      final Consumer<List<ProcessInstance>> asserter) {
    await()
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessInstanceQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });

    return self();
  }
}
