package org.camunda.bpm.broker.it.process;

import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class ProcessModels
{

    public static final BpmnModelInstance ONE_TASK_PROCESS = wrap(Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask1")
            .endEvent()
            .done())
        .taskAttributes("serviceTask1", "foo", 0);

    public static final BpmnModelInstance TWO_TASKS_PROCESS = wrap(Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask1")
            .serviceTask("serviceTask2")
            .endEvent()
            .done())
        .taskAttributes("serviceTask1", "foo", 0)
        .taskAttributes("serviceTask2", "bar", 0);


}
