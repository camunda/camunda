package org.camunda.optimize.qa.performance.steps.decorator;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.DataGenerationStep;
import ru.yandex.qatools.allure.annotations.Step;

import java.util.Collection;

public class HeatMapDataGenerationStep extends DataGenerationStep {

  @Override
  protected BpmnModelInstance createBpmnModel() {
    return Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .endEvent()
      .done();
  }

  @Override
  protected Collection<FlowNode> extractFlowNodes(BpmnModelInstance instance) {
    return instance.getModelElementsByType(FlowNode.class);
  }

  @Override
  @Step("Generate heatmap data")
  public PerfTestStepResult execute(PerfTestContext context) {
    return super.execute(context);
  }
}
