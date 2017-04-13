package org.camunda.tngp.broker.workflow.graph.transformer.metadata;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.tngp.broker.workflow.graph.model.metadata.IOMapping;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.*;

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

        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, IO_MAPPING_ELEMENT);

        ioMapping.setInputQuery(getMappingQuery(ioMappingElement, IO_MAPPING_INPUT_ATTRIBUTE));
        ioMapping.setOutputQuery(getMappingQuery(ioMappingElement, IO_MAPPING_OUTPUT_ATTRIBUTE));

        return ioMapping;
    }

    private static JsonPathQuery getMappingQuery(ModelElementInstance ioMappingElement, String attributeName)
    {
        final JsonPathQueryCompiler jsonPathQueryCompiler = new JsonPathQueryCompiler();

        String mappingValue = DEFAULT_MAPPING;
        if (ioMappingElement != null)
        {
            final String attributeValue = ioMappingElement.getAttributeValue(attributeName);
            if (attributeValue != null && !attributeValue.isEmpty())
            {
                mappingValue = attributeValue;
            }
        }

        final JsonPathQuery jsonPathQuery = jsonPathQueryCompiler.compile(mappingValue);

        if (!jsonPathQuery.isValid())
        {
            throw new RuntimeException("Mapping failed JSON Path Query is not valid! Reason: " + jsonPathQuery.getErrorReason());
        }
        return jsonPathQuery;
    }
}
