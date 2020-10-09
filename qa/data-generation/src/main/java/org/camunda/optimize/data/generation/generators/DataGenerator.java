/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.optimize.data.generation.generators.impl.incident.ActiveIncidentResolver;
import org.camunda.optimize.data.generation.generators.impl.incident.IdleIncidentResolver;
import org.camunda.optimize.data.generation.generators.impl.incident.IncidentResolver;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public abstract class DataGenerator<ModelType extends ModelInstance> implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  protected int nVersions;
  private int instanceCountToGenerate;
  private MessageEventCorrelater messageEventCorrelater;
  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  protected List<String> tenants = new ArrayList<>();

  protected SimpleEngineClient engineClient;
  private final AtomicInteger startedInstanceCount = new AtomicInteger(0);

  public DataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    this.nVersions = nVersions == null ? generateVersionNumber() : nVersions;
    generateTenants();
    this.engineClient = engineClient;
  }

  protected abstract ModelType retrieveDiagram();

  protected abstract Map<String, Object> createVariables();

  protected boolean createsIncidents() {
    return false;
  }

  public int getInstanceCountToGenerate() {
    return instanceCountToGenerate;
  }

  public void setInstanceCountToGenerate(int instanceCountToGenerate) {
    this.instanceCountToGenerate = instanceCountToGenerate;
  }

  public void addToInstanceCount(int numberOfInstancesToAdd) {
    this.instanceCountToGenerate += numberOfInstancesToAdd;
  }

  private int generateVersionNumber() {
    return ThreadLocalRandom.current().nextInt(5, 25);
  }

  @Override
  public void run() {
    logger.info("Start {}...", getClass().getSimpleName());
    final ModelType instance = retrieveDiagram();
    try {
      this.tenants.stream()
        .filter(Objects::nonNull)
        .forEach(tenantId -> engineClient.createTenant(tenantId));
      createMessageEventCorrelater();
      List<String> definitionIds = deployDiagrams(instance);
      deployAdditionalDiagrams();
      List<Integer> instanceSizePerDefinition = createInstanceSizePerDefinition();
      startInstances(instanceSizePerDefinition, definitionIds);
    } catch (Exception e) {
      logger.error("Error while generating the data", e);
    } finally {
      logger.info("{} finished data generation!", getClass().getSimpleName());
    }
  }

  protected abstract List<String> deployDiagrams(final ModelType instance);

  protected void generateTenants() {
    this.tenants = Collections.singletonList(null);
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


    Map<String, Object> variables = new HashMap<>();
    variables.put("person", complexVariableDto);
    int integer = RandomUtils.nextInt();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", RandomUtils.nextBoolean());
    variables.put("integerVar", RandomUtils.nextInt());
    variables.put("shortVar", (short) integer);
    variables.put("longVar", RandomUtils.nextLong());
    variables.put("doubleVar", RandomUtils.nextDouble());
    variables.put("dateVar", new Date(RandomUtils.nextInt()));
    return variables;
  }

  private void createMessageEventCorrelater() {
    messageEventCorrelater = new MessageEventCorrelater(engineClient, getCorrelationNames());
  }

  private IncidentResolver createIncidentResolver() {
    if (createsIncidents()) {
      return new ActiveIncidentResolver(engineClient);
    } else {
      return new IdleIncidentResolver();
    }
  }

  protected String[] getCorrelationNames() {
    return new String[]{};
  }

  private void startInstances(final List<Integer> batchSizes,
                              final List<String> definitionIds) {
    final IncidentResolver incidentResolver = createIncidentResolver();
    for (int ithBatch = 0; ithBatch < batchSizes.size(); ithBatch++) {
      final String definitionId = definitionIds.get(ithBatch);
      final UserTaskCompleter userTaskCompleter = new UserTaskCompleter(definitionId, engineClient);
      userTaskCompleter.startUserTaskCompletion();
      IntStream
        .range(0, batchSizes.get(ithBatch))
        .forEach(i -> {
          final Map<String, Object> variables = createVariables();
          variables.putAll(createSimpleVariables());
          startInstanceWithBackoff(definitionId, variables);
          try {
            Thread.sleep(5L);
          } catch (InterruptedException e) {
            logger.warn("Got interrupted while sleeping starting single instance");
            Thread.currentThread().interrupt();
          }
          incrementStartedInstanceCount();
          if (i % 1000 == 0) {
            if (messageEventCorrelater.getMessagesToCorrelate().length > 0) {
              messageEventCorrelater.correlateMessages();
              incidentResolver.resolveIncidents();
            }
          }
        });

      messageEventCorrelater.correlateMessages();
      incidentResolver.resolveIncidents();
      logger.info("[definition-id:{}] Finished batch execution", definitionId);

      logger.info("[definition-id:{}] Awaiting user task completion.", definitionId);
      try {
        userTaskCompleter.shutdown();
        userTaskCompleter.awaitUserTaskCompletion(Integer.MAX_VALUE, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        logger.warn("Got interrupted while waiting for userTask completion");
        Thread.currentThread().interrupt();
      }
      logger.info("[definition-id:{}] User tasks completion finished.", definitionId);
    }
  }

  private void startInstanceWithBackoff(final String definitionId, final Map<String, Object> variables) {
    boolean couldStartInstance = false;
    while (!couldStartInstance) {
      try {
        startInstance(definitionId, variables);
        couldStartInstance = true;
      } catch (Exception exception) {
        logError(exception);
        long timeToSleep = backoffCalculator.calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        sleep(timeToSleep);
      }
    }
    backoffCalculator.resetBackoff();
  }

  protected abstract void startInstance(final String definitionId, final Map<String, Object> variables);

  private void sleep(long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing to fetch new entities.", e);
      Thread.currentThread().interrupt();
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Sleeping for [{}] ms and retrying to start instance afterwards.",
      sleepTime
    );
  }

  private void logError(Exception e) {
    logger.error("Error during start of instance. Please check the connection!", e);
  }


  private List<Integer> createInstanceSizePerDefinition() {
    int deploymentCount = nVersions * tenants.size();
    int maxBulkSizeCount = instanceCountToGenerate / deploymentCount;
    int finalBulkSize = instanceCountToGenerate % deploymentCount;
    LinkedList<Integer> bulkSizes = new LinkedList<>(Collections.nCopies(deploymentCount, maxBulkSizeCount));
    if (finalBulkSize > 0) {
      bulkSizes.addLast(bulkSizes.removeLast() + finalBulkSize);
    }
    return bulkSizes;
  }

  public int getStartedInstanceCount() {
    return startedInstanceCount.get();
  }

  private void incrementStartedInstanceCount() {
    startedInstanceCount.incrementAndGet();
  }

}
