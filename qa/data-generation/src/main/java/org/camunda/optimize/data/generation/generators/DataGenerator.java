/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public abstract class DataGenerator implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private int nVersions;
  private int instanceCountToGenerate;
  private MessageEventCorrelater messageEventCorrelater;
  private BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  protected List<String> tenants = new ArrayList<>();

  protected SimpleEngineClient engineClient;
  private AtomicInteger startedInstanceCount = new AtomicInteger(0);

  public DataGenerator(SimpleEngineClient engineClient) {
    generateVersionNumber();
    this.engineClient = engineClient;
  }

  protected abstract BpmnModelInstance retrieveDiagram();

  protected abstract Map<String, Object> createVariablesForProcess();

  public int getInstanceCountToGenerate() {
    return instanceCountToGenerate;
  }

  public void setInstanceCountToGenerate(int instanceCountToGenerate) {
    this.instanceCountToGenerate = instanceCountToGenerate;
  }

  public void addToInstanceCount(int numberOfInstancesToAdd) {
    this.instanceCountToGenerate += numberOfInstancesToAdd;
  }

  private void generateVersionNumber() {
    nVersions = ThreadLocalRandom.current().nextInt(5, 25);
  }

  @Override
  public void run() {
    logger.info("Start {}...", getClass().getSimpleName());
    final BpmnModelInstance instance = retrieveDiagram();
    try {
      createMessageEventCorrelater();
      generateTenantsList();
      List<String> processDefinitionIds = engineClient.deployProcesses(instance, nVersions, tenants);
      deployAdditionalDiagrams();
      List<Integer> processInstanceSizePerDefinition = createProcessInstanceSizePerDefinition();
      startProcessInstances(processInstanceSizePerDefinition, processDefinitionIds);
    } catch (Exception e) {
      logger.error("Error while generating the data", e);
    } finally {
      logger.info("{} finished data generation!", getClass().getSimpleName());
    }
  }

  private void generateTenantsList() {
    int max = ThreadLocalRandom.current().nextInt(1, 5);
    for (int i = 1; i <= max; i++) {
      if (i == 4) {
        tenants.add(null);
        continue;
      }
      tenants.add("tenant" + i);
    }
  }

  protected void deployAdditionalDiagrams() {
    // this method allows the data generator to deploy
    // more than the default diagram. For instance, if you call another
    // diagram in you process by executing call activity or a dmn table.
  }

  private Map<String, Object> createSimpleVariables() {
    Map<String, Object> person = new HashMap<>();
    person.put("name", "Kermit");
    person.put("age", 50);
    ObjectMapper objectMapper = new ObjectMapper();
    String personAsString = null;
    try {
      personAsString = objectMapper.writeValueAsString(person);
    } catch (JsonProcessingException e) {
      logger.warn("Could not serialize complex variable!", e);
    }
    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(personAsString);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("java.lang.Object");
    info.setSerializationDataFormat("application/json");
    complexVariableDto.setValueInfo(info);


    Random random = new Random();
    Map<String, Object> variables = new HashMap<>();
    variables.put("person", complexVariableDto);
    Integer integer = random.nextInt();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", random.nextBoolean());
    variables.put("integerVar", random.nextInt());
    variables.put("shortVar", integer.shortValue());
    variables.put("longVar", random.nextLong());
    variables.put("doubleVar", random.nextDouble());
    variables.put("dateVar", new Date(random.nextInt()));
    return variables;
  }

  private void createMessageEventCorrelater() {
    messageEventCorrelater = new MessageEventCorrelater(engineClient, getCorrelationNames());
  }

  protected String[] getCorrelationNames() {
    return new String[]{};
  }

  private void startProcessInstances(final List<Integer> batchSizes,
                                     final List<String> processDefinitionIds) {
    for (int ithBatch = 0; ithBatch < batchSizes.size(); ithBatch++) {
      final String processDefinitionId = processDefinitionIds.get(ithBatch);
      final UserTaskCompleter userTaskCompleter = new UserTaskCompleter(processDefinitionId, engineClient);
      userTaskCompleter.startUserTaskCompletion();
      IntStream
        .range(0, batchSizes.get(ithBatch))
        .forEach(i -> {
          final Map<String, Object> variables = createVariablesForProcess();
          variables.putAll(createSimpleVariables());
          startProcessInstance(processDefinitionId, variables);
          try {
            Thread.sleep(5L);
          } catch (InterruptedException e) {
            logger.warn("Got interrupted while sleeping starting single instance");
          }
          incrementStartedInstanceCount();
          if (i % 1000 == 0) {
            if (messageEventCorrelater.getMessagesToCorrelate().length > 0) {
              messageEventCorrelater.correlateMessages();
            }
          }
        });

      messageEventCorrelater.correlateMessages();
      logger.info("[process-definition-id:{}] Finished batch execution", processDefinitionId);

      logger.info("[process-definition-id:{}] Awaiting user task completion.", processDefinitionId);
      userTaskCompleter.shutdown();
      userTaskCompleter.awaitUserTaskCompletion(Integer.MAX_VALUE, TimeUnit.SECONDS);
      logger.info("[process-definition-id:{}] User tasks completion finished.", processDefinitionId);

      if (Thread.currentThread().isInterrupted()) {
        return;
      }
    }

  }

  private void startProcessInstance(final String procDefId, final Map<String, Object> variables) {
    boolean couldStartProcessInstance = false;
    while (!couldStartProcessInstance) {
      try {
        engineClient.startProcessInstance(procDefId, variables);
        couldStartProcessInstance = true;
      } catch (Exception exception) {
        logError(exception);
        long timeToSleep = backoffCalculator.calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        sleep(timeToSleep);
      }
    }
    backoffCalculator.resetBackoff();
  }

  private void sleep(long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing to fetch new entities.", e);
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Sleeping for [{}] ms and retrying to start process instance afterwards.",
      sleepTime
    );
  }

  private void logError(Exception e) {
    logger.error("Error during start of process instance. Please check the connection with [{}]!", e);
  }


  private List<Integer> createProcessInstanceSizePerDefinition() {
    int deploymentCount = nVersions * tenants.size();
    int maxBulkSizeCount = instanceCountToGenerate / deploymentCount;
    int finalBulkSize = instanceCountToGenerate % deploymentCount;
    LinkedList<Integer> bulkSizes = new LinkedList<>(Collections.nCopies(deploymentCount, maxBulkSizeCount));
    if (finalBulkSize > 0) {
      bulkSizes.addLast(bulkSizes.removeLast() + finalBulkSize);
    }
    return bulkSizes;
  }

  public BpmnModelInstance readProcessDiagramAsInstance(String diagramPath) {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }

  protected DmnModelInstance readDmnTableAsInstance(String dmnPath) {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(dmnPath);
    return Dmn.readModelFromStream(inputStream);
  }

  public int getStartedInstanceCount() {
    return startedInstanceCount.get();
  }

  private void incrementStartedInstanceCount() {
    startedInstanceCount.incrementAndGet();
  }

}
