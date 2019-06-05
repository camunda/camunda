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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static java.util.concurrent.CompletableFuture.runAsync;

public abstract class DataGenerator implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private int nVersions;
  private int instanceCountToGenerate;
  private MessageEventCorrelater messageEventCorrelater;
  private BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);

  protected SimpleEngineClient engineClient;

  public DataGenerator(SimpleEngineClient engineClient) {
    generateVersionNumber();
    this.engineClient = engineClient;
  }

  protected abstract BpmnModelInstance retrieveDiagram();

  protected abstract Map<String, Object> createVariablesForProcess();

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
    BpmnModelInstance instance = retrieveDiagram();
    try {
      startCorrelatingMessages();
      List<String> processDefinitionIds = engineClient.deployProcesses(instance, nVersions);
      deployAdditionalDiagrams();
      List<Integer> processInstanceSizePerDefinition = createProcessInstanceSizePerDefinition();
      startProcessInstances(processInstanceSizePerDefinition, processDefinitionIds);
      messageEventCorrelater.correlateMessages();
    } catch (Exception e) {
      logger.error("Error while generating the data", e);
    } finally {
      stopCorrelatingMessages();
      logger.info("{} finished data generation!", getClass().getSimpleName());
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

  private void startCorrelatingMessages() {
    messageEventCorrelater =
      new MessageEventCorrelater(engineClient, getCorrelationNames());
    messageEventCorrelater.startCorrelatingMessages();
  }

  private void stopCorrelatingMessages() {
    if (messageEventCorrelater != null) {
      messageEventCorrelater.stopCorrelatingMessages();
    }
  }

  protected String[] getCorrelationNames() {
    return new String[]{};
  }

  private void startProcessInstances(final List<Integer> batchSizes, final List<String> processDefinitionIds) {
    final ExecutorService instanceStartExecutorService = Executors.newFixedThreadPool(20);
    try {
      for (int ithBatch = 0; ithBatch < batchSizes.size(); ithBatch++) {
        final String procDefId = processDefinitionIds.get(ithBatch);
        final CompletableFuture[] startInstanceFutures = IntStream
          .range(0, batchSizes.get(ithBatch))
          .mapToObj(taskDto -> runAsync(
            () -> {
              final Map<String, Object> variables = createVariablesForProcess();
              variables.putAll(createSimpleVariables());
              startProcessInstance(procDefId, variables);
            },
            instanceStartExecutorService
          ))
          .toArray(CompletableFuture[]::new);

        try {
          CompletableFuture.allOf(startInstanceFutures).get();
        } catch (InterruptedException | ExecutionException e) {
          logger.error("Failed batch execution", e);
        }

        if (Thread.currentThread().isInterrupted()) {
          return;
        }
      }
    } finally {
      instanceStartExecutorService.shutdown();
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
    int maxBulkSizeCount = instanceCountToGenerate / nVersions;
    int finalBulkSize = instanceCountToGenerate % nVersions;
    LinkedList<Integer> bulkSizes = new LinkedList<>(Collections.nCopies(nVersions, maxBulkSizeCount));
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
}
