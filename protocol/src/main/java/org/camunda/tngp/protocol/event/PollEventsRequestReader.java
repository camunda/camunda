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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class PollEventsRequestReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PollEventsDecoder requestDecoder = new PollEventsDecoder();

    protected final UnsafeBuffer topicNameBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        requestDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += PollEventsDecoder.topicNameHeaderLength();

        topicNameBuffer.wrap(buffer, offset, requestDecoder.topicNameLength());
    }

    public long startPosition()
    {
        return requestDecoder.startPosition();
    }

    public int maxEvents()
    {
        return requestDecoder.maxEvents();
    }

    public DirectBuffer topicName()
    {
        return topicNameBuffer;
    }

}
