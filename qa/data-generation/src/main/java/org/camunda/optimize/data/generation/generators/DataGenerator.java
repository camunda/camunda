/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.data.generation.generators.impl.incident.ActiveIncidentResolver;
import org.camunda.optimize.data.generation.generators.impl.incident.IdleIncidentResolver;
import org.camunda.optimize.data.generation.generators.impl.incident.IncidentResolver;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
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
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public abstract class DataGenerator<ModelType extends ModelInstance> implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  protected int nVersions;
  private int instanceCountToGenerate;
  private MessageEventCorrelater messageEventCorrelater;
  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  protected List<String> tenants = new ArrayList<>();
  private final List<String> objectVarNames =
    List.of("Meggle", "Omran", "Helene", "Andrea", "Asia", "Giuliano", "Josh", "Kyrylo", "Eric", "Cigdem");
  private final List<String> objectVarHobbies =
    List.of("Optimizing", "Garlic eating", "Knitting", "Sleeping", "Competitive duck herding",
            "Candy Crush", "Ferret racing", "Planking", "Tapdancing", "Armwrestling"
    );

  protected final SimpleEngineClient engineClient;
  private final UserAndGroupProvider userAndGroupProvider;
  private final AtomicInteger startedInstanceCount = new AtomicInteger(0);
  private final ObjectMapper objectMapper;

  public DataGenerator(final SimpleEngineClient engineClient,
                       final Integer nVersions,
                       final UserAndGroupProvider userAndGroupProvider) {
    this.nVersions = nVersions == null ? generateVersionNumber() : nVersions;
    this.engineClient = engineClient;
    this.userAndGroupProvider = userAndGroupProvider;
    this.objectMapper = new ObjectMapper().setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT));
    generateTenants();
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
        .forEach(engineClient::createTenant);
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

  private Map<String, Object> createDefaultVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("person", createPersonObjectVariable());
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", RandomUtils.nextBoolean());
    variables.put("integerVar", RandomUtils.nextInt());
    variables.put("shortVar", (short) RandomUtils.nextInt());
    variables.put("longVar", RandomUtils.nextLong());
    variables.put("doubleVar", RandomUtils.nextDouble());
    variables.put("dateVar", new Date(RandomUtils.nextInt()));
    variables.put("numberList", createListVariable(List.of(
      RandomUtils.nextInt(0, 100),
      -RandomUtils.nextInt(0, 100),
      RandomUtils.nextInt(0, 100)
    )));
    variables.put("dateList", createListVariable(List.of(
      new Date(OffsetDateTime.now().minusYears(RandomUtils.nextInt(0, 20)).toInstant().toEpochMilli()),
      new Date(OffsetDateTime.now().plusYears(RandomUtils.nextInt(0, 20)).toInstant().toEpochMilli())
    )));
    return variables;
  }

  private VariableDto createPersonObjectVariable() {
    final Map<String, Object> person = new HashMap<>();
    person.put("name", objectVarNames.get(RandomUtils.nextInt(0, objectVarNames.size())));
    person.put("age", RandomUtils.nextInt(25, 45));
    person.put(
      "hobbies",
      Stream.of(
        objectVarHobbies.get(RandomUtils.nextInt(0, objectVarHobbies.size())),
        objectVarHobbies.get(RandomUtils.nextInt(0, objectVarHobbies.size()))
      ).collect(toSet())
    );
    person.put(
      "favouriteDay",
      new Date(OffsetDateTime.now().minusYears(RandomUtils.nextInt(10, 100)).toInstant().toEpochMilli())
    );
    final Map<String, Boolean> skillsMap = Map.of(
      "read", RandomUtils.nextBoolean(),
      "write", RandomUtils.nextBoolean()
    );
    person.put("skills", skillsMap);

    String personAsString = null;
    try {
      personAsString = objectMapper.writeValueAsString(person);
    } catch (JsonProcessingException e) {
      logger.warn("Could not serialize object variable!", e);
    }
    VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName("java.lang.Object");
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);

    return new VariableDto()
      .setType(EngineConstants.VARIABLE_TYPE_OBJECT)
      .setValue(personAsString)
      .setValueInfo(info);
  }

  public VariableDto createListVariable(final List<Object> listValues) {
    String listVarAsString = null;
    try {
      listVarAsString = objectMapper.writeValueAsString(listValues);
    } catch (JsonProcessingException e) {
      logger.warn("Could not serialize object variable!", e);
    }
    VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName("java.util.ArrayList");
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);

    return new VariableDto()
      .setType(EngineConstants.VARIABLE_TYPE_OBJECT)
      .setValue(listVarAsString)
      .setValueInfo(info);
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
      logger.info("[definition-id:{}] Starting batch execution", definitionId);
      final UserTaskCompleter userTaskCompleter = new UserTaskCompleter(
        definitionId, engineClient, userAndGroupProvider
      );
      userTaskCompleter.startUserTaskCompletion();
      try {
        IntStream
          .range(0, batchSizes.get(ithBatch))
          .forEach(i -> {
            final Map<String, Object> variables = createVariables();
            variables.putAll(createDefaultVariables());
            startInstanceWithBackoff(definitionId, variables);
            try {
              Thread.sleep(5L);
            } catch (InterruptedException e) {
              logger.warn(
                "Got interrupted while sleeping starting single instance for definition id: {}", definitionId
              );
              Thread.currentThread().interrupt();
            }
            incrementStartedInstanceCount();
            if (i % 1000 == 0) {
              correlateMessagesAndResolveIncidents(incidentResolver);
            }
          });

        correlateMessagesAndResolveIncidents(incidentResolver);

        logger.info("[definition-id:{}] Finished batch execution", definitionId);
      } finally {
        try {
          logger.info("[definition-id:{}] Awaiting user task completion.", definitionId);
          userTaskCompleter.shutdown();
          userTaskCompleter.awaitUserTaskCompletion(Integer.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          logger.warn("Got interrupted while waiting for userTask completion");
          Thread.currentThread().interrupt();
        }
        logger.info("[definition-id:{}] User tasks completion finished.", definitionId);
      }
    }
  }

  private void correlateMessagesAndResolveIncidents(final IncidentResolver incidentResolver) {
    if (messageEventCorrelater.getMessagesToCorrelate().length > 0) {
      messageEventCorrelater.correlateMessages();
    }
    incidentResolver.resolveIncidents();
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
