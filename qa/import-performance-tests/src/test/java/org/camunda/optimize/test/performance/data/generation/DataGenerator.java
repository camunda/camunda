package org.camunda.optimize.test.performance.data.generation;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public abstract class DataGenerator implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private int nVersions;
  private int instanceCountToGenerate;

  private SimpleEngineClient engineClient;

  public DataGenerator() {
    generateVersionNumber();
    engineClient = new SimpleEngineClient();
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
    List<String> processDefinitionIds = engineClient.deployProcesses(instance, nVersions);
    List<Integer> processInstanceSizePerDefinition = createProcessInstanceSizePerDefinition();
    try {
      startProcessInstances(processInstanceSizePerDefinition, processDefinitionIds);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      engineClient.close();
      logger.info("{} finished data generation!", getClass().getSimpleName());
    }
  }

  private void startProcessInstances(List<Integer> batchSizes, List<String> processDefinitionIds) throws IOException {
    for (int ithBatch = 0; ithBatch < batchSizes.size(); ithBatch++) {
      for (int i = 0; i < batchSizes.get(ithBatch); i++) {
        Map<String, Object> variables = createVariablesForProcess();
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
