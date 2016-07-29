package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnEventReaderTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    @Test
    public void testReadBpmnFlowElementEvent()
    {
        // given
        final BpmnEventReader eventReader = new BpmnEventReader();
        final BpmnFlowElementEventWriter writer = new BpmnFlowElementEventWriter();

        writer
            .eventType(ExecutionEventType.EVT_OCCURRED)
            .flowElementId(123)
            .key(456L)
            .processId(789L)
            .workflowInstanceId(9875L)
            .write(buffer, 0);

        // when
        eventReader.wrap(buffer, 0, writer.getLength());

        // then
        assertThat(eventReader.templateId()).isEqualTo(BpmnFlowElementEventDecoder.TEMPLATE_ID);

        final BpmnFlowElementEventReader flowElementEvent = eventReader.flowElementEvent();

        assertThat(flowElementEvent.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED);
        assertThat(flowElementEvent.flowElementId()).isEqualTo(123);
        assertThat(flowElementEvent.key()).isEqualTo(456L);
        assertThat(flowElementEvent.wfDefinitionId()).isEqualTo(789L);
        assertThat(flowElementEvent.wfInstanceId()).isEqualTo(9875L);
    }

    @Test
    public void testReadUnknownTemplate()
    {
        // given
        final BpmnEventReader eventReader = new BpmnEventReader();
        final WfDefinitionWriter writer = new WfDefinitionWriter();

        writer
            .prevVersionPosition(1)
            .resource(new UnsafeBuffer(new byte[0]), 0, 0)
            .resourceId(123)
            .wfDefinitionKey(new byte[]{1, 2, 3})
            .write(buffer, 0);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Unsupported template");

        // when
        eventReader.wrap(buffer, 0, writer.getLength());
    }

}
