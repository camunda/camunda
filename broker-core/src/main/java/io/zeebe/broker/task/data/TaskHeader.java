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
package io.zeebe.broker.task.data;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class TaskHeader extends UnpackedObject
{
    private final StringProperty keyProp = new StringProperty("key");
    private final StringProperty valueProp = new StringProperty("value");

    public TaskHeader()
    {
        this.declareProperty(keyProp)
            .declareProperty(valueProp);
    }

    public DirectBuffer getKey()
    {
        return keyProp.getValue();
    }

    public TaskHeader setKey(String key)
    {
        this.keyProp.setValue(key);
        return this;
    }

    public DirectBuffer getValue()
    {
        return valueProp.getValue();
    }

    public TaskHeader setValue(String value)
    {
        this.valueProp.setValue(value);
        return this;
    }

}
