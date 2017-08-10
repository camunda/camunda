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

import javax.xml.bind.annotation.XmlElement;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.metadata.InputOutputMappingImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.TaskDefinition;

public class ServiceTaskImpl extends FlowNodeImpl implements ServiceTask
{
    private ExtensionElementsImpl extensionElements;

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS, namespace = BpmnConstants.BPMN20_NS)
    public void setExtensionElements(ExtensionElementsImpl extensionElements)
    {
        this.extensionElements = extensionElements;
    }

    public ExtensionElementsImpl getExtensionElements()
    {
        return extensionElements;
    }

    @Override
    public TaskDefinition getTaskDefinition()
    {
        return extensionElements != null ? extensionElements.getTaskDefinition() : null;
    }

    @Override
    public TaskHeadersImpl getTaskHeaders()
    {
        return extensionElements != null ? extensionElements.getTaskHeaders() : null;
    }

    @Override
    public InputOutputMappingImpl getInputOutputMapping()
    {
        return extensionElements != null ? extensionElements.getInputOutputMapping() : null;
    }

}
