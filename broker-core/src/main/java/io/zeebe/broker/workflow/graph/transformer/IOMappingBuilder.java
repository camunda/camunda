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
package io.zeebe.broker.workflow.graph.transformer;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.INPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.OUTPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;

/**
 * Represents a builder to create the IO mapping fluently.
 */
public class IOMappingBuilder
{
    public static final String MAPPING_ATTRIBUTE_SOURCE = "source";
    public static final String MAPPING_ATTRIBUTE_TARGET = "target";

    private final ZeebeModelInstance zeebeModelInstance;
    private final ModelElementInstance ioMapping;

    public IOMappingBuilder(ZeebeModelInstance zeebeModelInstance, ModelElementInstance ioMapping)
    {
        this.ioMapping = ioMapping;
        this.zeebeModelInstance = zeebeModelInstance;
    }

    public IOMappingBuilder input(String source, String target)
    {
        addMappingElement(INPUT_MAPPING_ELEMENT, source, target);
        return this;
    }

    public IOMappingBuilder output(String source, String target)
    {
        addMappingElement(OUTPUT_MAPPING_ELEMENT, source, target);
        return this;
    }

    public ZeebeModelInstance done()
    {
        return zeebeModelInstance;
    }

    private void addMappingElement(String mappingElement, String source, String target)
    {
        final DomElement inputMapping = zeebeModelInstance.getDocument().createElement(ZEEBE_NAMESPACE, mappingElement);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_SOURCE, source);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_TARGET, target);
        ioMapping.getDomElement().appendChild(inputMapping);
    }
}
