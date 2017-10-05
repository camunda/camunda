/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.snapshot;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.util.buffer.BufferUtil;

public class FooObject extends UnpackedObject
{
    protected final IntegerProperty prop1 = new IntegerProperty("prop1");
    protected final StringProperty prop2 = new StringProperty("prop2");

    public FooObject()
    {
        declareProperty(prop1)
            .declareProperty(prop2);
    }

    public void setProp1(int val)
    {
        this.prop1.setValue(val);
    }

    public int getProp1()
    {
        return prop1.getValue();
    }

    public void setProp2(String val)
    {
        this.prop2.setValue(val);
    }

    public String getProp2()
    {
        return BufferUtil.bufferAsString(this.prop2.getValue());
    }
}
