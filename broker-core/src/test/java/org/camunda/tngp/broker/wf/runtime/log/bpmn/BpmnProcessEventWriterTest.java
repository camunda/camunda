package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnFlowElementEventDecoder;
import org.camunda.tngp.protocol.log.BpmnFlowElementEventEncoder;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.protocol.log.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class BpmnProcessEventWriterTest
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
        final BpmnFlowElementEventWriter writer = new BpmnFlowElementEventWriter();

        // when
        writer
            .eventType(ExecutionEventType.EVT_OCCURRED)
            .flowElementId(75)
            .key(1234L)
            .processId(8765L)
            .workflowInstanceId(45678L)
            .write(eventBuffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final BpmnFlowElementEventDecoder bodyDecoder = new BpmnFlowElementEventDecoder();

        headerDecoder.wrap(eventBuffer, 0);
        assertThat(headerDecoder.blockLength()).isEqualTo(BpmnFlowElementEventDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(BpmnFlowElementEventDecoder.SCHEMA_ID);
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(BpmnFlowElementEventDecoder.TEMPLATE_ID);

        bodyDecoder.wrap(eventBuffer,
                MessageHeaderDecoder.ENCODED_LENGTH,
                BpmnFlowElementEventDecoder.BLOCK_LENGTH,
                BpmnFlowElementEventDecoder.SCHEMA_VERSION);
        assertThat(bodyDecoder.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED.value());
        assertThat(bodyDecoder.flowElementId()).isEqualTo(75);
        assertThat(bodyDecoder.key()).isEqualTo(1234L);
        assertThat(bodyDecoder.wfDefinitionId()).isEqualTo(8765L);
        assertThat(bodyDecoder.wfInstanceId()).isEqualTo(45678L);
    }

    @Test
    public void shouldEstimateWriteLength()
    {
        // given
        final BpmnFlowElementEventWriter writer = new BpmnFlowElementEventWriter();

        writer
            .eventType(ExecutionEventType.EVT_OCCURRED)
            .flowElementId(75)
            .key(1234L)
            .processId(8765L)
            .workflowInstanceId(45678L)
            .write(eventBuffer, 0);

        // when
        final int estimatedLength = writer.getLength();

        // then
        assertThat(estimatedLength).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + BpmnFlowElementEventEncoder.BLOCK_LENGTH + BpmnFlowElementEventEncoder.flowElementIdStringHeaderLength());
    }
}
