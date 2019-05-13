/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.transform;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.deployment.model.yaml.BpmnYamlParser;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.slf4j.Logger;

public class DeploymentTransformer {
  private static final Logger LOG = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  private final BpmnValidator validator = new BpmnValidator();
  private final BpmnYamlParser yamlParser = new BpmnYamlParser();
  private final WorkflowState workflowState;
  private final KeyGenerator keyGenerator;

  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentTransformer(final ZeebeState zeebeState) {
    this.workflowState = zeebeState.getWorkflowState();
    this.keyGenerator = zeebeState.getKeyGenerator();
  }

  public boolean transform(final DeploymentRecord deploymentEvent) {
    final StringBuilder validationErrors = new StringBuilder();
    boolean success = true;
    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
    if (!resourceIterator.hasNext()) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = "Expected to deploy at least one resource, but none given";
      return false;
    }

    while (resourceIterator.hasNext()) {
      final DeploymentResource deploymentResource = resourceIterator.next();
      success &= transformResource(deploymentEvent, validationErrors, deploymentResource);
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following validation errors:%s",
              validationErrors.toString());
    }

    return success;
  }

  private boolean transformResource(
      final DeploymentRecord deploymentEvent,
      final StringBuilder validationErrors,
      final DeploymentResource deploymentResource) {
    boolean success = true;
    try {
      final BpmnModelInstance definition = readWorkflowDefinition(deploymentResource);
      final String validationError = validator.validate(definition);

      if (validationError == null) {
        transformWorkflowResource(deploymentEvent, deploymentResource, definition);
      } else {
        validationErrors
            .append("\n'")
            .append(bufferAsString(deploymentResource.getResourceName()))
            .append("': ")
            .append(validationError);
        success = false;
      }
    } catch (RuntimeException e) {
      final String resourceName = bufferAsString(deploymentResource.getResourceName());
      LOG.error("Unexpected error while processing resource '{}'", resourceName, e);

      validationErrors.append("\n'").append(resourceName).append("': ").append(e.getMessage());
      success = false;
    }
    return success;
  }

  private void transformWorkflowResource(
      final DeploymentRecord deploymentEvent,
      final DeploymentResource deploymentResource,
      final BpmnModelInstance definition) {
    final Collection<Process> processes =
        definition.getDefinitions().getChildElementsByType(Process.class);

    for (final Process workflow : processes) {
      if (workflow.isExecutable()) {
        final String bpmnProcessId = workflow.getId();

        final long key = keyGenerator.nextKey();
        final int version = workflowState.getNextWorkflowVersion(bpmnProcessId);

        deploymentEvent
            .workflows()
            .add()
            .setBpmnProcessId(BufferUtil.wrapString(workflow.getId()))
            .setVersion(version)
            .setKey(key)
            .setResourceName(deploymentResource.getResourceName());
      }
    }

    transformYamlWorkflowResource(deploymentResource, definition);
  }

  private BpmnModelInstance readWorkflowDefinition(final DeploymentResource deploymentResource) {
    final DirectBuffer resource = deploymentResource.getResource();
    final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);

    switch (deploymentResource.getResourceType()) {
      case YAML_WORKFLOW:
        return yamlParser.readFromStream(resourceStream);
      case BPMN_XML:
      default:
        return Bpmn.readModelFromStream(resourceStream);
    }
  }

  private void transformYamlWorkflowResource(
      final DeploymentResource deploymentResource, final BpmnModelInstance definition) {
    if (deploymentResource.getResourceType() != ResourceType.BPMN_XML) {
      final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
      Bpmn.writeModelToStream(outStream, definition);

      final DirectBuffer bpmnXml = BufferUtil.wrapArray(outStream.toByteArray());
      deploymentResource.setResource(bpmnXml);
    }
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }
}
