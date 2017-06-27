package io.zeebe.broker.workflow.graph.transformer.metadata;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_DEFINITION_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADERS_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_KEY_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_HEADER_VALUE_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_RETRIES_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.TASK_TYPE_ATTRIBUTE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;
import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata;
import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata.TaskHeader;
import io.zeebe.util.buffer.BufferUtil;

public class TaskMetadataTransformer
{
    private static final int DEFAULT_TASK_RETRIES = 3;

    private static final TaskHeader[] EMPTY_TASK_HEADERS = new TaskHeader[0];

    public static TaskMetadata transform(ExtensionElements extensionElements)
    {
        final TaskMetadata metadata = new TaskMetadata();

        // TODO #202 - provide Zeebe model instance
        final ModelElementInstance taskDefinition = getTaskDefinition(extensionElements);

        final String type = getTaskType(taskDefinition);
        metadata.setTaskType(BufferUtil.wrapString(type));

        final int retries = getTaskRetries(taskDefinition);
        metadata.setRetries(retries);

        final TaskHeader[] taskHeaders = getTaskHeaders(extensionElements);
        metadata.setHeaders(taskHeaders);

        return metadata;
    }

    private static ModelElementInstance getTaskDefinition(ExtensionElements extensionElements)
    {
        final ModelElementInstance taskDefinition = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);
        ensureNotNull("task definition", taskDefinition);
        return taskDefinition;
    }

    private static String getTaskType(final ModelElementInstance taskDefinition)
    {
        final String type = taskDefinition.getAttributeValue(TASK_TYPE_ATTRIBUTE);
        ensureNotNull("task type", type);
        return type;
    }

    private static int getTaskRetries(final ModelElementInstance taskDefinition)
    {
        int retries = DEFAULT_TASK_RETRIES;

        final String configuredRetries = taskDefinition.getAttributeValue(TASK_RETRIES_ATTRIBUTE);
        if (configuredRetries != null && !configuredRetries.isEmpty())
        {
            try
            {
                retries = Integer.parseInt(configuredRetries);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException("Failed to parse task retries. Expected number but found: " + configuredRetries);
            }
        }

        return retries;
    }

    private static TaskHeader[] getTaskHeaders(ExtensionElements extensionElements)
    {
        TaskHeader[] taskHeaders = EMPTY_TASK_HEADERS;

        final ModelElementInstance taskHeadersElement = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_HEADERS_ELEMENT);
        if (taskHeadersElement != null)
        {
            final List<DomElement> headerElements = taskHeadersElement.getDomElement().getChildElementsByNameNs(ZEEBE_NAMESPACE, TASK_HEADER_ELEMENT);

            taskHeaders = new TaskHeader[headerElements.size()];

            for (int i = 0; i < headerElements.size(); i++)
            {
                final DomElement header = headerElements.get(i);

                final String key = header.getAttribute(TASK_HEADER_KEY_ATTRIBUTE);
                final String value = header.getAttribute(TASK_HEADER_VALUE_ATTRIBUTE);

                taskHeaders[i] = new TaskHeader(key, value);
            }
        }
        return taskHeaders;
    }
}
