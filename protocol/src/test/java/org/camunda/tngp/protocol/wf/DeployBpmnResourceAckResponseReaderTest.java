package org.camunda.tngp.protocol.wf;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class DeployBpmnResourceAckResponseReaderTest
{

    @Test
    public void testReadResponse()
    {
        // given
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);

        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final DeployBpmnResourceAckEncoder bodyEncoder = new DeployBpmnResourceAckEncoder();

        headerEncoder.wrap(buffer, 0).schemaId(bodyEncoder.sbeSchemaId()).templateId(bodyEncoder.sbeTemplateId())
                .version(bodyEncoder.sbeSchemaVersion()).blockLength(bodyEncoder.sbeBlockLength()).shardId(52)
                .resourceId(123);
        bodyEncoder.wrap(buffer, headerEncoder.encodedLength()).wfDefinitionId(1234);

        // when
        final DeployBpmnResourceAckResponseReader reader = new DeployBpmnResourceAckResponseReader();
        reader.wrap(buffer, 0, headerEncoder.encodedLength() + bodyEncoder.encodedLength());

        // then
        assertThat(reader.wfDefinitionId()).isEqualTo(1234);
    }
}
