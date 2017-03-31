package org.camunda.tngp.broker.workflow.graph.transformer.metadata;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_DEFINITION_ELEMENT;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_RETRIES_ATTRIBUTE;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_TYPE_ATTRIBUTE;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TNGP_NAMESPACE;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;

public class TaskMetadataTransformer
{
    private static final int DEFAULT_TASK_RETRIES = 3;

    public static TaskMetadata transform(ExtensionElements extensionElements)
    {
        final TaskMetadata metadata = new TaskMetadata();

        // TODO #202 - provide TNGP model instance
        final ModelElementInstance taskDefinition = getTaskDefinition(extensionElements);

        final String type = getTaskType(taskDefinition);
        metadata.setTaskType(type);

        final int retries = getTaskRetries(taskDefinition);
        metadata.setRetries(retries);

        return metadata;
    }

    private static ModelElementInstance getTaskDefinition(ExtensionElements extensionElements)
    {
        final ModelElementInstance taskDefinition = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, TASK_DEFINITION_ELEMENT);
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
        if (configuredRetries != null)
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

}
