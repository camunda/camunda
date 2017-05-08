package org.camunda.tngp.broker.workflow.graph.transformer.metadata;

import org.agrona.Strings;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.tngp.broker.workflow.graph.model.metadata.IOMapping;
import org.camunda.tngp.broker.workflow.graph.model.metadata.Mapping;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;

import java.util.List;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.*;

/**
 * Transforms given extension elements to an IOMapping for
 * flow elements.
 */
public class IOMappingTransformer
{
    private static final String DEFAULT_MAPPING = "$";

    private static final int SINGLE_MAPPING_COUNT = 1;
    private static final int FIRST_MAPPING_IDX = 0;

    public static IOMapping transform(ExtensionElements extensionElements)
    {
        final IOMapping ioMapping = new IOMapping();

        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, IO_MAPPING_ELEMENT);

        List<DomElement> inputMappingElements = null;
        List<DomElement> outputMappingElements = null;
        if (ioMappingElement != null)
        {
            final DomElement domElement = ioMappingElement.getDomElement();
            inputMappingElements = domElement.getChildElementsByNameNs(TNGP_NAMESPACE, INPUT_MAPPING_ELEMENT);
            outputMappingElements = domElement.getChildElementsByNameNs(TNGP_NAMESPACE, OUTPUT_MAPPING_ELEMENT);
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
            mappings = new Mapping[SINGLE_MAPPING_COUNT];
            mappings[FIRST_MAPPING_IDX] = createMapping(null);
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

    private static Mapping createMapping(DomElement mappingElement)
    {
        final String sourceMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_SOURCE);
        final String targetMapping = getMappingQuery(mappingElement, MAPPING_ATTRIBUTE_TARGET);

        //TODO make json path compiler re-usable!
        return new Mapping(new JsonPathQueryCompiler().compile(sourceMapping),
                           new JsonPathQueryCompiler().compile(targetMapping),
                           targetMapping);
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
