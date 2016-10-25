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
import org.camunda.tngp.client.event.Event;

public class WorkflowDefinitionRequestEventImpl implements Event
{
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_NEW_DEPLOYMENT = 0;

    private long position;

    private DirectBuffer rawBuffer;

    private int type;

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

    public int getType()
    {
        return type;
    }

    public void setType(int type)
    {
        this.type = type;
    }

    public String getResource()
    {
        return resource;
    }

    public void setResource(String resource)
    {
        this.resource = resource;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public void setRawBuffer(DirectBuffer rawBuffer)
    {
        this.rawBuffer = rawBuffer;
    }

    public boolean isNewDeployentRequest()
    {
        return type == TYPE_NEW_DEPLOYMENT;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowDefinitionRequestEventImpl [type=");
        builder.append(type);
        builder.append("]");
        return builder.toString();
    }

}
