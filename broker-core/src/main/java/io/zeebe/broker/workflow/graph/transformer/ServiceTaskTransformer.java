/**
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

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;

import io.zeebe.broker.workflow.graph.model.ExecutableScope;
import io.zeebe.broker.workflow.graph.model.ExecutableServiceTask;
import io.zeebe.broker.workflow.graph.model.metadata.IOMapping;
import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata;
import io.zeebe.broker.workflow.graph.transformer.metadata.IOMappingTransformer;
import io.zeebe.broker.workflow.graph.transformer.metadata.TaskMetadataTransformer;

public class ServiceTaskTransformer implements BpmnElementTransformer<ServiceTask, ExecutableServiceTask>
{
    @Override
    public Class<ServiceTask> getType()
    {
        return ServiceTask.class;
    }

    @Override
    public void transform(ServiceTask modelElement, ExecutableServiceTask bpmnElement, ExecutableScope scope)
    {
        final ExtensionElements extensionElements = modelElement.getExtensionElements();
        ensureNotNull("extension elements", extensionElements);

        final TaskMetadata taskMetadata = TaskMetadataTransformer.transform(extensionElements);
        ensureNotNull("task metadata", taskMetadata);
        bpmnElement.setTaskMetadata(taskMetadata);

        final IOMapping ioMapping = IOMappingTransformer.transform(extensionElements);
        ensureNotNull("task io mapping", ioMapping);
        bpmnElement.setIoMapping(ioMapping);
    }
}
