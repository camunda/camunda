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
package io.zeebe.broker.workflow.graph.transformer.metadata;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.INPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.IO_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.MAPPING_ATTRIBUTE_SOURCE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.MAPPING_ATTRIBUTE_TARGET;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.OUTPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;
import static io.zeebe.msgpack.mapping.Mapping.JSON_ROOT_PATH;

import java.util.List;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.Strings;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import io.zeebe.broker.workflow.graph.model.metadata.IOMapping;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;

/**
 * Transforms given extension elements to an IOMapping for
 * flow elements.
 */
public class IOMappingTransformer
{
    private static final String DEFAULT_MAPPING = "$";

    public static IOMapping transform(ExtensionElements extensionElements)
    {
        final IOMapping ioMapping = new IOMapping();

        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, IO_MAPPING_ELEMENT);

        List<DomElement> inputMappingElements = null;
        List<DomElement> outputMappingElements = null;
        if (ioMappingElement != null)
        {
            final DomElement domElement = ioMappingElement.getDomElement();
            inputMappingElements = domElement.getChildElementsByNameNs(ZEEBE_NAMESPACE, INPUT_MAPPING_ELEMENT);
            outputMappingElements = domElement.getChildElementsByNameNs(ZEEBE_NAMESPACE, OUTPUT_MAPPING_ELEMENT);
        }

        ioMapping.setInputMappings(createMappings(inputMappingElements));
        ioMapping.setOutputMappings(createMappings(outputMappingElements));

        return ioMapping;
    }

    private static Mapping[] createMappings(List<DomElement> mappingElements)
    {
        final Mapping mappings[];

        if (mappingElements == null || mappingElements.isEmpty())
        {
            mappings = new Mapping[0];
            // need no mappings
        }
        else if (mappingElements.size() == 1)
        {
            mappings = createNonRootMapping(mappingElements.get(0));
        }
        else
        {
            mappings = new Mapping[mappingElements.size()];

            for (int i = 0; i < mappingElements.size(); i++)
            {
                final DomElement mappingElement = mappingElements.get(i);
                mappings[i] = createMapping(mappingElement);
            }
        }
        return mappings;
    }

    private static Mapping[] createNonRootMapping(DomElement mappingElement)
    {
        final String sourceMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_SOURCE);
        final String targetMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_TARGET);

        final Mapping[] mappings;
        if (sourceMapping.equals(JSON_ROOT_PATH) && targetMapping.equals(JSON_ROOT_PATH))
        {
            mappings = new Mapping[0];
        }
        else
        {
            mappings = new Mapping[]{new Mapping(new JsonPathQueryCompiler().compile(sourceMapping),
                                                 BufferUtil.wrapString(targetMapping))};
        }

        return mappings;
    }

    private static Mapping createMapping(DomElement mappingElement)
    {
        final String sourceMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_SOURCE);
        final String targetMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_TARGET);

        //TODO make json path compiler re-usable!
        return new Mapping(new JsonPathQueryCompiler().compile(sourceMapping),
                           BufferUtil.wrapString(targetMapping));
    }

    private static String getMappingQuery(DomElement mappingElement, String attributeName)
    {
        String mappingValue = DEFAULT_MAPPING;
        if (mappingElement != null)
        {
            final String mapping = mappingElement.getAttribute(attributeName);
            if (!Strings.isEmpty(mapping))
            {
                mappingValue = mapping;
            }
        }
        return mappingValue;
    }
}
