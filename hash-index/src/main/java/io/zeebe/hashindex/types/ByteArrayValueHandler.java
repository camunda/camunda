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
package io.zeebe.hashindex.types;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.hashindex.IndexValueHandler;

public class ByteArrayValueHandler implements IndexValueHandler
{
    public byte[] theValue;

    @Override
    public void readValue(DirectBuffer buffer, int offset, int length)
    {
        buffer.getBytes(offset, theValue, 0, length);
    }

    @Override
    public void writeValue(MutableDirectBuffer buffer, int offset, int length)
    {
        buffer.putBytes(offset, theValue);
    }

}
