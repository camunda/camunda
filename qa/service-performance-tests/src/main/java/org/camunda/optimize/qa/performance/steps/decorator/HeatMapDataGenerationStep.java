package org.camunda.optimize.qa.performance.steps.decorator;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.DataGenerationStep;
import org.camunda.optimize.qa.performance.util.IdGenerator;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import ru.yandex.qatools.allure.annotations.Step;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class HeatMapDataGenerationStep extends DataGenerationStep {

  @Override
  protected BpmnModelInstance createBpmnModel() {
    return Bpmn.createExecutableProcess("aProcess")
      .startEvent(startEventActivityId)
      .endEvent()
      .done();
  }

  @Override
  @Step("Generate heatmap data")
  public PerfTestStepResult execute(PerfTestContext context) {
    return super.execute(context);
  }

  @Override
  protected int addGeneratedData(BulkRequestBuilder bulkRequest, BpmnModelInstance modelInstance, String processDefinitionKey) {

    Collection<FlowNode> flowNodes = extractFlowNodes(modelInstance);
    String processInstanceId = "processInstance:" + IdGenerator.getNextId();
    for (FlowNode flowNode : flowNodes) {
      String id = IdGenerator.getNextId();
      XContentBuilder source = generateSource(
          id,
          flowNode.getId(),
          processDefinitionKey,
          processInstanceId
      );
      bulkRequest
        .add(client
          .prepareIndex(
              context.getConfiguration().getOptimizeIndex(),
              context.getConfiguration().getProcessInstanceType(),
              id
          )
          .setSource(source)
        );
    }
    return flowNodes.size();
  }

  private Collection<FlowNode> extractFlowNodes(BpmnModelInstance instance) {
    return instance.getModelElementsByType(FlowNode.class);
  }

  private XContentBuilder generateSource(String id, String activityId, String processDefinitionKey, String processInstanceId) {
    try {
      String date = sdf.format(new Date());
      return jsonBuilder()
        .startObject()
          .startArray("events")
            .startObject()
              .field("id", id)
              .field("activityId", activityId)
              .field("durationInMs", 20)
              .field("activityType", "flowNode")
            .endObject()
          .endArray()
          .startArray("stringVariables")
            .startObject()
              .field("id", "Var" + id)
              .field("name", "var")
              .field("type", "String")
              .field("value", "aStringValue")
            .endObject()
          .endArray()
          .field("processDefinitionKey", processDefinitionKey)
          .field("processDefinitionId", context.getParameter("processDefinitionId"))
          .field("processInstanceId", processInstanceId)
          .field("startDate", date)
          .field("endDate", date)
        .endObject();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

}
