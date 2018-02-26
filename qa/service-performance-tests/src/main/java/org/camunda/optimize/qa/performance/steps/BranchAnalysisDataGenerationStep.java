package org.camunda.optimize.qa.performance.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.IdGenerator;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import ru.yandex.qatools.allure.annotations.Step;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class BranchAnalysisDataGenerationStep extends DataGenerationStep {

  private final String END_ACTIVITY = "endActivity";
  private final String GATEWAY_ACTIVITY = "gw_1";

  @Override
  @Step ("Generate Correlation discovery data")
  public PerfTestStepResult execute(PerfTestContext context) {
    PerfTestStepResult result = super.execute(context);
    context.addParameter("endActivityId", END_ACTIVITY);
    context.addParameter("gatewayActivityId", GATEWAY_ACTIVITY);
    return result;
  }

  @Override
  protected BpmnModelInstance createBpmnModel() {
    return Bpmn.createProcess()
      .startEvent(startEventActivityId)
      .serviceTask()
      .exclusiveGateway(GATEWAY_ACTIVITY)
        .userTask()
        .endEvent(END_ACTIVITY)
      .moveToLastGateway()
        .userTask()
        .endEvent()
      .done();
  }

  protected void addModelToElasticsearch(BpmnModelInstance instance) {
    String xml = Bpmn.convertToString(instance);
    ProcessDefinitionXmlOptimizeDto dto = new ProcessDefinitionXmlOptimizeDto();
    dto.setBpmn20Xml(xml);
    String processDefinitionId = context.getParameter("processDefinitionId").toString();
    dto.setProcessDefinitionId( processDefinitionId);
    ObjectMapper mapper = new ObjectMapper();
    try {

      client.prepareIndex(
          context.getConfiguration().getIndexForType("process-definition-xml"),
          "process-definition-xml",
          processDefinitionId
        )
        .setSource(mapper.writeValueAsString(dto), XContentType.JSON)
        .get();

      client.admin().indices()
          .prepareRefresh(context.getConfiguration().getIndexForType("process-definition-xml"))
          .get();
    } catch (JsonProcessingException e) {
      throw new PerfTestException("Failed to add model to elasticsearch! Could not write object to string!", e);
    }
  }

  private List<List<FlowNode>> extractProcessInstancePaths(BpmnModelInstance instance) {
    List<FlowNode> flowNodes = new LinkedList<>();
    List<List<FlowNode>> processInstanceFlows = new LinkedList<>();
    Collection<StartEvent> startEvents = instance.getModelElementsByType(StartEvent.class);
    for (StartEvent startEvent : startEvents) {
      walkThroughModel(processInstanceFlows, flowNodes, startEvent);
    }
    return processInstanceFlows;
  }

  /**
   * Walks recursively through the model and when an exclusive gateway is reached,
   * a new path is created and added to the list of process instance paths.
   */
  private void walkThroughModel(List<List<FlowNode>> processInstancePath, List<FlowNode> currentPath, FlowNode currentNode) {
    currentPath.add(currentNode);
    Collection<SequenceFlow> sequenceFlows = currentNode.getOutgoing();
    if (sequenceFlows.isEmpty()) {
      processInstancePath.add(currentPath);
    }
    for (SequenceFlow sequenceFlow : sequenceFlows) {
      List<FlowNode> currentPathCopy = new LinkedList<>(currentPath);
      walkThroughModel(processInstancePath, currentPathCopy, sequenceFlow.getTarget());
    }
  }

  @Override
  protected int addGeneratedData(BulkRequestBuilder bulkRequest, BpmnModelInstance modelInstance, String processDefinitionKey) {

    List<List<FlowNode>> processInstanceFlows = extractProcessInstancePaths(modelInstance);
    for (List<FlowNode> processInstanceFlow : processInstanceFlows) {
      String processInstanceId = "processInstance:" + IdGenerator.getNextId();
      List<String> activityList = new LinkedList<>();
      for (FlowNode flowNode : processInstanceFlow) {
        activityList.add(flowNode.getId());
      }
      XContentBuilder source = generateSource(activityList, processInstanceId);
      bulkRequest
        .add(client
          .prepareIndex(
            context.getConfiguration().getIndexForType(context.getConfiguration().getProcessInstanceType()),
            context.getConfiguration().getProcessInstanceType(),
            processInstanceId
          )
          .setSource(source)
        );
    }
    return processInstanceFlows.size();
  }

  private XContentBuilder generateSource(List<String> activityList, String processInstanceId) {
    try {
      String date = sdf.format(new Date());
      XContentBuilder source = jsonBuilder()
        .startObject()
          .startArray("events");
            addEvents(source, activityList)
          .endArray()
          .startArray("stringVariables")
            .startObject()
              .field("id", "Var" + IdGenerator.getNextId())
              .field("name", "var")
              .field("type", "String")
              .field("value", "aStringValue")
            .endObject()
          .endArray()
          .field("processDefinitionKey", IdGenerator.getNextId())
          .field("processDefinitionId", context.getParameter("processDefinitionId"))
          .field("processInstanceId", processInstanceId)
          .field("startDate", date)
          .field("endDate", date)
        .endObject();
      return source;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private XContentBuilder addEvents(XContentBuilder contentBuilder, List<String> activityList) throws IOException {
    for (String event : activityList) {
      contentBuilder
        .startObject()
          .field("id", IdGenerator.getNextId())
          .field("activityId", event)
          .field("durationInMs", 20)
          .field("activityType", "flowNode")
        .endObject();
    }
    return contentBuilder;
  }
}
