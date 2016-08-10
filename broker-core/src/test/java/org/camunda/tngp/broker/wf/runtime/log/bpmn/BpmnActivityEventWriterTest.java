package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnActivityEventWriterTest
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
        final BpmnActivityEventWriter writer = new BpmnActivityEventWriter();

        // when
        writer
            .eventType(ExecutionEventType.ACT_INST_CREATED)
            .key(1234L)
            .wfDefinitionId(8765L)
            .wfInstanceId(45678L)
            .flowElementId(5678)
            .taskQueueId(7)
            .taskType(new UnsafeBuffer("foobar".getBytes(StandardCharsets.UTF_8)), 3, 3)
            .write(eventBuffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final BpmnActivityEventDecoder bodyDecoder = new BpmnActivityEventDecoder();

        headerDecoder.wrap(eventBuffer, 0);
        assertThat(headerDecoder.blockLength()).isEqualTo(BpmnActivityEventDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(BpmnActivityEventDecoder.SCHEMA_ID);
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(BpmnActivityEventDecoder.TEMPLATE_ID);

        bodyDecoder.wrap(eventBuffer,
                MessageHeaderDecoder.ENCODED_LENGTH,
                BpmnActivityEventDecoder.BLOCK_LENGTH,
                BpmnActivityEventDecoder.SCHEMA_VERSION);
        assertThat(bodyDecoder.event()).isEqualTo(ExecutionEventType.ACT_INST_CREATED.value());
        assertThat(bodyDecoder.key()).isEqualTo(1234L);
        assertThat(bodyDecoder.wfDefinitionId()).isEqualTo(8765L);
        assertThat(bodyDecoder.wfInstanceId()).isEqualTo(45678L);
        assertThat(bodyDecoder.flowElementId()).isEqualTo(5678);
        assertThat(bodyDecoder.taskQueueId()).isEqualTo(7);
        assertThat(bodyDecoder.taskType()).isEqualTo("bar");
    }

    @Test
    public void shouldEstimateWriteLength()
    {
        // given
        final BpmnActivityEventWriter writer = new BpmnActivityEventWriter();

        writer
            .eventType(ExecutionEventType.ACT_INST_CREATED)
            .key(1234L)
            .wfDefinitionId(8765L)
            .wfInstanceId(45678L)
            .flowElementId(5678)
            .taskQueueId(7)
            .taskType(new UnsafeBuffer("foobar".getBytes(StandardCharsets.UTF_8)), 3, 3)
            .write(eventBuffer, 0);

        // when
        final int estimatedLength = writer.getLength();

        // then
        assertThat(estimatedLength).isEqualTo(
                MessageHeaderEncoder.ENCODED_LENGTH +
                BpmnActivityEventEncoder.BLOCK_LENGTH +
                BpmnActivityEventEncoder.taskTypeHeaderLength() +
                3);
    }


}
