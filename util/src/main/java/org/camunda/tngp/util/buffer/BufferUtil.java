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
package org.camunda.tngp.util.buffer;

import java.nio.charset.Charset;

import org.agrona.DirectBuffer;

public final class BufferUtil
{
    public static final Charset UTF_8_CHARSET = Charset.forName("utf-8");

    private BufferUtil()
    { // avoid instantiation of util class
    }

    public static String bufferAsString(final DirectBuffer buffer)
    {
        final byte[] bytes = new byte[buffer.capacity()];

        buffer.getBytes(0, bytes);

        return new String(bytes, UTF_8_CHARSET);
    }

}
