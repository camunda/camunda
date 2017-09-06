/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.instance.*;
import org.junit.Test;

public class BpmnAspectTest
{

    @Test
    public void shouldSetTakeSequenceFlowAspectIfOutgoingSequenceFlowExists()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("foo"))
            .endEvent("end")
            .done();

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

        final StartEvent startEvent = workflow.findFlowElementById(wrapString("start"));
        assertThat(startEvent.getBpmnAspect()).isEqualTo(BpmnAspect.TAKE_SEQUENCE_FLOW);

        final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
        assertThat(serviceTask.getBpmnAspect()).isEqualTo(BpmnAspect.TAKE_SEQUENCE_FLOW);
    }

    @Test
    public void shouldSetConsumeTokenAspectOnEndEvent()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .endEvent("end")
            .done();

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

        final EndEvent endEvent = workflow.findFlowElementById(wrapString("end"));
        assertThat(endEvent.getBpmnAspect()).isEqualTo(BpmnAspect.CONSUME_TOKEN);
    }

    @Test
    public void shouldSetConsumeTokenAspectIfNoOutgoiningSequenceFlowExists()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("foo"))
            .done();

        final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

        final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
        assertThat(serviceTask.getBpmnAspect()).isEqualTo(BpmnAspect.CONSUME_TOKEN);
    }

}
