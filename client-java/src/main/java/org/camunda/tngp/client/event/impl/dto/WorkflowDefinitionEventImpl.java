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
package org.camunda.tngp.client.event.impl.dto;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.event.WorkflowDefinitionEvent;

public class WorkflowDefinitionEventImpl implements WorkflowDefinitionEvent
{
    private long position;

    private DirectBuffer rawBuffer;

    private long id;

    private String key;

    private String resource;

    @Override
    public long getPosition()
    {
        return position;
    }

    @Override
    public DirectBuffer getRawBuffer()
    {
        return rawBuffer;
    }

    @Override
    public long getId()
    {
        return id;
    }

    @Override
    public String getKey()
    {
        return key;
    }

    @Override
    public String getResource()
    {
        return resource;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public void setRawBuffer(DirectBuffer rawBuffer)
    {
        this.rawBuffer = rawBuffer;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public void setResource(String resource)
    {
        this.resource = resource;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowDefinitionEventImpl [id=");
        builder.append(id);
        builder.append(", key=");
        builder.append(key);
        builder.append("]");
        return builder.toString();
    }

}
