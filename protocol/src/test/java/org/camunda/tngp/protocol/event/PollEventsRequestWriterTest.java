/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PollEventsRequestWriterTest
{
    protected static final int OFFSET = 21;

    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldWriteRequest()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(0)
            .maxEvents(10)
            .topicId(1);

        // when
        requestWriter.write(buffer, OFFSET);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final PollEventsDecoder requestDecoder = new PollEventsDecoder();

        headerDecoder.wrap(buffer, OFFSET);

        assertThat(headerDecoder.blockLength()).isEqualTo(PollEventsDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.schemaId()).isEqualTo(PollEventsDecoder.SCHEMA_ID);
        assertThat(headerDecoder.templateId()).isEqualTo(PollEventsDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.version()).isEqualTo(PollEventsDecoder.SCHEMA_VERSION);

        requestDecoder.wrap(buffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(requestDecoder.startPosition()).isEqualTo(0);
        assertThat(requestDecoder.maxEvents()).isEqualTo(10);
        assertThat(requestDecoder.topicId()).isEqualTo(1);
    }

    @Test
    public void shouldReturnLength()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(0)
            .maxEvents(10)
            .topicId(1);

        // when
        final int length = requestWriter.getLength();

        // then
        assertThat(length).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH + PollEventsDecoder.BLOCK_LENGTH);
    }

    @Test
    public void shouldResetAfterWrite()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(0)
            .maxEvents(10)
            .topicId(1);

        // when
        requestWriter.write(buffer, OFFSET);
        requestWriter.write(buffer, OFFSET);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final PollEventsDecoder requestDecoder = new PollEventsDecoder();

        headerDecoder.wrap(buffer, OFFSET);

        requestDecoder.wrap(buffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(requestDecoder.startPosition()).isEqualTo(PollEventsDecoder.startPositionNullValue());
        assertThat(requestDecoder.maxEvents()).isEqualTo(PollEventsDecoder.maxEventsNullValue());
        assertThat(requestDecoder.topicId()).isEqualTo(PollEventsDecoder.topicIdNullValue());
    }

    @Test
    public void shouldValidateStartPosition()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(-1)
            .maxEvents(10)
            .topicId(1);

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("start position must be greater or equal to 0");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldValidateMaxEvents()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(0)
            .maxEvents(0)
            .topicId(1);

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("max events must be greater than 0");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldValidateTopicId()
    {
        // given
        final PollEventsRequestWriter requestWriter = new PollEventsRequestWriter();

        requestWriter
            .startPosition(0)
            .maxEvents(10)
            .topicId(-1);

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("topic id must be greater or equal to 0");

        // when
        requestWriter.validate();
    }

}
