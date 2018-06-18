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
package io.zeebe.broker.system.workflow.repository.processor;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.workflow.repository.data.*;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.model.bpmn.BpmnModelApi;
import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import org.agrona.DirectBuffer;

public class DeploymentCreateEventProcessor implements TypedRecordProcessor<DeploymentRecord> {
  private final BpmnModelApi bpmn = new BpmnModelApi();

  private final WorkflowRepositoryIndex index;

  private boolean accepted;
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentCreateEventProcessor(WorkflowRepositoryIndex index) {
    this.index = index;
  }

  @Override
  public void processRecord(TypedRecord<DeploymentRecord> event) {
    final DeploymentRecord deploymentEvent = event.getValue();
    final String topicName = bufferAsString(deploymentEvent.getTopicName());

    if (topicExists(topicName)) {
      accepted = readAndValidateWorkflows(deploymentEvent, topicName);
    } else {
      accepted = false;
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = "Topic does not exist";
    }
  }

  @Override
  public long writeRecord(TypedRecord<DeploymentRecord> event, TypedStreamWriter writer) {
    if (accepted) {
      return writer.writeFollowUpEvent(
          event.getKey(),
          DeploymentIntent.CREATED,
          event.getValue(),
          m ->
              m.requestId(event.getMetadata().getRequestId())
                  .requestStreamId(event.getMetadata().getRequestStreamId()));
    } else {
      return writer.writeRejection(
          event,
          rejectionType,
          rejectionReason,
          m ->
              m.requestId(event.getMetadata().getRequestId())
                  .requestStreamId(event.getMetadata().getRequestStreamId()));
    }
  }

  private boolean topicExists(String topicName) {
    return index.checkTopicExists(topicName);
  }

  private boolean readAndValidateWorkflows(
      final DeploymentRecord deploymentEvent, String topicName) {
    final StringBuilder validationErrors = new StringBuilder();

    boolean success = true;

    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();

    if (!resourceIterator.hasNext()) {
      validationErrors.append("Deployment doesn't contain a resource to deploy");

      success = false;
    } else {
      // TODO: only one resource is supported; turn resources into a property

      final DeploymentResource deploymentResource = resourceIterator.next();

      try {
        final WorkflowDefinition definition = readWorkflowDefinition(deploymentResource);

        for (Workflow workflow : definition.getWorkflows()) {
          if (workflow.isExecutable()) {
            final String bpmnProcessId = BufferUtil.bufferAsString(workflow.getBpmnProcessId());
            final long key = index.getNextKey();
            final int version = index.getNextVersion(topicName, bpmnProcessId);

            deploymentEvent
                .deployedWorkflows()
                .add()
                .setBpmnProcessId(workflow.getBpmnProcessId())
                .setVersion(version)
                .setKey(key)
                .setResourceName(deploymentResource.getResourceName());
          }
        }

        transformWorkflowResource(deploymentResource, definition);
      } catch (InvalidModelException ex) {
        validationErrors.append(
            String.format(
                "Resource '%s':\n", bufferAsString(deploymentResource.getResourceName())));
        validationErrors.append(ex.getMessage());
        success = false;
      } catch (Exception e) {
        validationErrors.append(
            String.format(
                "Failed to deploy resource '%s':\n",
                bufferAsString(deploymentResource.getResourceName())));
        validationErrors.append(generateErrorMessage(e));

        success = false;
      }
    }

    if (!success) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = validationErrors.toString();
    }

    return success;
  }

  private WorkflowDefinition readWorkflowDefinition(DeploymentResource deploymentResource) {
    final DirectBuffer resource = deploymentResource.getResource();

    switch (deploymentResource.getResourceType()) {
      case BPMN_XML:
        return bpmn.readFromXmlBuffer(resource);

      case YAML_WORKFLOW:
        return bpmn.readFromYamlBuffer(resource);

      default:
        return bpmn.readFromXmlBuffer(resource);
    }
  }

  private void transformWorkflowResource(
      final DeploymentResource deploymentResource, final WorkflowDefinition definition) {
    if (deploymentResource.getResourceType() != ResourceType.BPMN_XML) {
      final DirectBuffer bpmnXml = wrapString(bpmn.convertToString(definition));
      deploymentResource.setResource(bpmnXml);
    }
  }

  private String generateErrorMessage(final Exception e) {
    final StringWriter stacktraceWriter = new StringWriter();

    e.printStackTrace(new PrintWriter(stacktraceWriter));

    return stacktraceWriter.toString();
  }
}
