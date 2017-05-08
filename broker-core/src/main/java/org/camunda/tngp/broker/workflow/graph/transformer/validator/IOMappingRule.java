package org.camunda.tngp.broker.workflow.graph.transformer.validator;

import org.agrona.Strings;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.camunda.tngp.broker.workflow.graph.model.metadata.Mapping;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;

import java.util.List;
import java.util.regex.Pattern;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.*;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.INVALID_JSON_PATH_EXPRESSION;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.REDUNDANT_MAPPING;

/**
 * Represents the IO mapping validation rule.
 */
public class IOMappingRule implements ModelElementValidator<ExtensionElements>
{
    public static final String ERROR_MSG_PROHIBITED_EXPRESSION = "Mapping failed! JSON Path contains prohibited expression (for example $.* or $.(foo|bar)).";
    public static final String ERROR_MSG_INVALID_EXPRESSION = "Mapping failed JSON Path Query is not valid! Reason: %s";
    public static final String ERROR_MSG_REDUNDANT_MAPPING = "Mapping failed! If Root path is mapped other mapping (makes no sense) is disallowed.";

    private static final String PROHIBITED_EXPRESSIONS_REGEX = "(\\.\\*)|(\\[.*,.*\\])";
    private static final Pattern PROHIBITED_EXPRESSIONS = Pattern.compile(PROHIBITED_EXPRESSIONS_REGEX);

    @Override
    public Class<ExtensionElements> getElementType()
    {
        return ExtensionElements.class;
    }

    @Override
    public void validate(ExtensionElements extensionElements, ValidationResultCollector validationResultCollector)
    {
        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, IO_MAPPING_ELEMENT);

        if (ioMappingElement != null)
        {
            final DomElement domElement = ioMappingElement.getDomElement();
            final List<DomElement> inputMappingElements = domElement.getChildElementsByNameNs(TNGP_NAMESPACE, INPUT_MAPPING_ELEMENT);
            final List<DomElement> outputMappingElements = domElement.getChildElementsByNameNs(TNGP_NAMESPACE, OUTPUT_MAPPING_ELEMENT);

            validateMappings(validationResultCollector, inputMappingElements);
            validateMappings(validationResultCollector, outputMappingElements);
        }
    }

    private static void validateMappings(ValidationResultCollector validationResultCollector, List<DomElement> mappingElements)
    {
        if (mappingElements != null && !mappingElements.isEmpty())
        {
            for (int i = 0; i < mappingElements.size(); i++)
            {
                final DomElement mapping = mappingElements.get(i);

                validateMappingExpression(validationResultCollector, mapping, MAPPING_ATTRIBUTE_SOURCE);
                final boolean isRootMapping = validateMappingExpression(validationResultCollector, mapping, MAPPING_ATTRIBUTE_TARGET);

                if (isRootMapping && mappingElements.size() > 1)
                {
                    validationResultCollector.addError(REDUNDANT_MAPPING, ERROR_MSG_REDUNDANT_MAPPING);
                }
            }
        }
    }

    private static boolean validateMappingExpression(ValidationResultCollector validationResultCollector, DomElement mappingElement, String attributeName)
    {
        boolean isRootMapping = false;

        if (mappingElement != null)
        {
            final String mapping = mappingElement.getAttribute(attributeName);
            if (!Strings.isEmpty(mapping))
            {
                if (PROHIBITED_EXPRESSIONS.matcher(mapping).find())
                {
                    validationResultCollector.addError(PROHIBITED_JSON_PATH_EXPRESSION, ERROR_MSG_PROHIBITED_EXPRESSION);
                }

                if (mapping.equals(Mapping.JSON_ROOT_PATH))
                {
                    isRootMapping = true;
                }

                final JsonPathQuery jsonPathQuery = new JsonPathQueryCompiler().compile(mapping);
                if (!jsonPathQuery.isValid())
                {
                    validationResultCollector.addError(INVALID_JSON_PATH_EXPRESSION,
                        String.format(ERROR_MSG_INVALID_EXPRESSION, jsonPathQuery.getErrorReason()));
                }
            }
        }

        return isRootMapping;
    }
}
