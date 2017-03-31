package org.camunda.optimize.qa.performance.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import ru.yandex.qatools.allure.annotations.Step;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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
      .startEvent()
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
    dto.setId( processDefinitionId);
    ObjectMapper mapper = new ObjectMapper();
    try {

      client.prepareIndex(
          context.getConfiguration().getOptimizeIndex(),
          "process-definition-xml",
          processDefinitionId
        )
        .setSource(mapper.writeValueAsString(dto))
        .get();

      client.admin().indices()
          .prepareRefresh(context.getConfiguration().getOptimizeIndex())
          .get();
    } catch (JsonProcessingException e) {
      throw new PerfTestException("Failed to add model to elasticsearch! Could not write object to string!", e);
    }
  }

  @Override
  protected Collection<FlowNode> extractFlowNodes(BpmnModelInstance instance) {
    List<FlowNode> flowNodes = new LinkedList<>();
    Collection<StartEvent> startEvents = instance.getModelElementsByType(StartEvent.class);
    for (StartEvent startEvent : startEvents) {
      walkThroughModel(flowNodes, startEvent, 2);
    }
    return flowNodes;
  }

  /**
   * Walks recursively through the model and when an exclusive gateway is reached,
   * the number of created events is split up between the two paths.
   */
  private void walkThroughModel(List<FlowNode> flowNodes, FlowNode currentNode, int numberOfEventsToCreate) {
    for (int i = 0; i < numberOfEventsToCreate; i++) {
      flowNodes.add(currentNode);
    }
    Collection<SequenceFlow> sequenceFlows = currentNode.getOutgoing();
    if (sequenceFlows.size() > 1) {
      numberOfEventsToCreate = 1;
    }
    for (SequenceFlow sequenceFlow : sequenceFlows) {
      walkThroughModel(flowNodes, sequenceFlow.getTarget(), numberOfEventsToCreate);
    }
  }
}
