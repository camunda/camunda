package org.camunda.optimize.data.generation.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public abstract class DataGenerator implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private int nVersions;
  private int instanceCountToGenerate;
  private MessageEventCorrelater messageEventCorrelater;

  protected SimpleEngineClient engineClient;

  public DataGenerator(SimpleEngineClient engineClient) {
    generateVersionNumber();
    this.engineClient = engineClient;
  }

  protected abstract BpmnModelInstance retrieveDiagram();

  protected abstract Set<String> getPathVariableNames();

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
    info.setObjectTypeName("org.camunda.foo.Person");
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
    return new String[] {};
  }

  private void startProcessInstances(List<Integer> batchSizes, List<String> processDefinitionIds) throws IOException {
    for (int ithBatch = 0; ithBatch < batchSizes.size(); ithBatch++) {
      for (int i = 0; i < batchSizes.get(ithBatch); i++) {
        Map<String, Object> variables = createVariablesForProcess();
        variables.putAll(createSimpleVariables());
        engineClient.startProcessInstance(processDefinitionIds.get(ithBatch), variables);
      }
    }
  }


  private List<Integer> createProcessInstanceSizePerDefinition() {
    LinkedList<Integer> bulkSizes = new LinkedList<>();
    int maxBulkSizeCount = instanceCountToGenerate / nVersions;
    int finalBulkSize = instanceCountToGenerate % nVersions;
    bulkSizes.addAll(Collections.nCopies(nVersions, maxBulkSizeCount));
    if (finalBulkSize > 0) {
      bulkSizes.addLast(bulkSizes.removeLast() + finalBulkSize);
    }
    return bulkSizes;
  }

  private Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    for (String variableName : getPathVariableNames()) {
      variables.put(variableName, ThreadLocalRandom.current().nextDouble());
    }
    return variables;
  }


  public BpmnModelInstance readDiagramAsInstance(String diagramPath) throws IOException {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }
}
