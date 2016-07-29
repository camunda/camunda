package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnFlowElementEventReaderTest
{

    protected UnsafeBuffer eventBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int eventLength;

    @Before
    public void writeEventToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final BpmnFlowElementEventEncoder bodyEncoder = new BpmnFlowElementEventEncoder();

        headerEncoder.wrap(eventBuffer, 0)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(1)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(2)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(eventBuffer, headerEncoder.encodedLength())
            .event(ExecutionEventType.EVT_OCCURRED.value())
            .flowElementId(75)
            .key(1234L)
            .wfDefinitionId(8765L)
            .wfInstanceId(45678L);

        eventLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadEvent()
    {
        // given
        final BpmnFlowElementEventReader reader = new BpmnFlowElementEventReader();

        // when
        reader.wrap(eventBuffer, 0, eventLength);

        // then
        assertThat(reader.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED);
        assertThat(reader.flowElementId()).isEqualTo(75);
        assertThat(reader.key()).isEqualTo(1234L);
        assertThat(reader.wfDefinitionId()).isEqualTo(8765L);
        assertThat(reader.wfInstanceId()).isEqualTo(45678L);
    }
}
