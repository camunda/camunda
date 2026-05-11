/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup.data;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBackupDataGenerator implements AutoCloseable {

  protected static final Duration DATA_TIMEOUT = Duration.ofSeconds(90);
  protected static final int SEARCH_LIMIT = 200;

  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected CamundaClient camundaClient;

  protected AbstractBackupDataGenerator(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public void close() {
    camundaClient = null;
  }

  public abstract void createData();

  public abstract void assertData();

  public abstract void changeData();

  public abstract void assertDataAfterChange();

  public void deployProcess(final String bpmnProcessId, final BpmnModelInstance model) {
    final var deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(model, bpmnProcessId + ".bpmn")
            .send()
            .join();
    logger.info("Deployed process {} with key {}", bpmnProcessId, deploymentEvent.getKey());
  }

  public List<Long> startProcessInstances(final String bpmnProcessId, final int count) {
    final List<Long> keys = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final long key =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .variables(Map.of("var1", "value1"))
              .send()
              .join()
              .getProcessInstanceKey();
      logger.debug("Started process instance {} for process {}", key, bpmnProcessId);
      keys.add(key);
    }
    logger.info("{} process instances started for {}", keys.size(), bpmnProcessId);
    return keys;
  }

  public List<ProcessInstance> searchProcessInstances(
      final String processDefinitionId, final ProcessInstanceState state) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId).state(state))
        .page(p -> p.limit(SEARCH_LIMIT).from(0))
        .send()
        .join()
        .items();
  }
}
