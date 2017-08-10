/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.FlowElement;
import org.agrona.DirectBuffer;

public class FlowElementImpl implements FlowElement
{
    private DirectBuffer id;
    private DirectBuffer name;

    @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_ID)
    @XmlID
    public void setId(String id)
    {
        this.id = wrapString(id);
    }

    public String getId()
    {
        return id != null ? bufferAsString(id) : null;
    }

    @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_NAME)
    public void setName(String name)
    {
        this.name = wrapString(name);
    }

    public String getName()
    {
        return name != null ? bufferAsString(name) : null;
    }

    @Override
    public DirectBuffer getIdAsBuffer()
    {
        return id;
    }

    public DirectBuffer getNameAsBuffer()
    {
        return name;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("FlowElement [id=");
        builder.append(getId());
        builder.append(", name=");
        builder.append(getName());
        builder.append("]");
        return builder.toString();
    }

}
