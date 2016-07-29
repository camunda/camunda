package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnFlowElementEventWriterTest
{

    protected UnsafeBuffer eventBuffer;

    @Before
    public void setUpBuffer()
    {
        eventBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    }

    @Test
    public void shouldWriteEvent()
    {
        // given
        final BpmnProcessEventWriter writer = new BpmnProcessEventWriter();

        // when
        writer
            .event(ExecutionEventType.EVT_OCCURRED)
            .initialElementId(75)
            .key(1234L)
            .processId(8765L)
            .processInstanceId(45678L)
            .write(eventBuffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final BpmnProcessEventDecoder bodyDecoder = new BpmnProcessEventDecoder();

        headerDecoder.wrap(eventBuffer, 0);
        assertThat(headerDecoder.blockLength()).isEqualTo(BpmnProcessEventDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(BpmnProcessEventDecoder.SCHEMA_ID);
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(BpmnProcessEventDecoder.TEMPLATE_ID);

        bodyDecoder.wrap(eventBuffer,
                MessageHeaderDecoder.ENCODED_LENGTH,
                BpmnProcessEventDecoder.BLOCK_LENGTH,
                BpmnProcessEventDecoder.SCHEMA_VERSION);
        assertThat(bodyDecoder.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED.value());
        assertThat(bodyDecoder.initialElementId()).isEqualTo(75);
        assertThat(bodyDecoder.key()).isEqualTo(1234L);
        assertThat(bodyDecoder.wfDefinitionId()).isEqualTo(8765L);
        assertThat(bodyDecoder.wfInstanceId()).isEqualTo(45678L);
    }

    @Test
    public void shouldEstimateWriteLength()
    {
        // given
        final BpmnProcessEventWriter writer = new BpmnProcessEventWriter();

        writer
            .event(ExecutionEventType.EVT_OCCURRED)
            .initialElementId(75)
            .key(1234L)
            .processId(8765L)
            .processInstanceId(45678L)
            .write(eventBuffer, 0);

        // when
        final int estimatedLength = writer.getLength();

        // then
        assertThat(estimatedLength).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + BpmnProcessEventEncoder.BLOCK_LENGTH);
    }


}
