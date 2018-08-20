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

import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.data.DeploymentResource;
import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.broker.workflow.model.yaml.BpmnYamlParser;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public class DeploymentTransformer {

  private final BpmnValidator validator = new BpmnValidator();
  private final BpmnYamlParser yamlParser = new BpmnYamlParser();
  private final WorkflowRepositoryIndex index;

  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentTransformer(WorkflowRepositoryIndex index) {
    this.index = index;
  }

  public boolean transform(final DeploymentRecord deploymentEvent) {
    final StringBuilder validationErrors = new StringBuilder();
    boolean success = true;
    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
    if (!resourceIterator.hasNext()) {
      validationErrors.append("Deployment doesn't contain a resource to deploy");
      success = false;
    } else {
      while (resourceIterator.hasNext()) {
        final DeploymentResource deploymentResource = resourceIterator.next();
        success = transformResource(deploymentEvent, validationErrors, deploymentResource);
      }
    }

    if (!success) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = validationErrors.toString();
    }

    return success;
  }

  private boolean transformResource(
      DeploymentRecord deploymentEvent,
      StringBuilder validationErrors,
      DeploymentResource deploymentResource) {
    boolean success = true;
    try {
      final BpmnModelInstance definition = readWorkflowDefinition(deploymentResource);
      final String validationError = validator.validate(definition);

      if (validationError == null) {
        transformWorkflowResource(deploymentEvent, deploymentResource, definition);
      } else {
        validationErrors.append(
            String.format(
                "Resource '%s':\n", bufferAsString(deploymentResource.getResourceName())));
        validationErrors.append(validationError);
        success = false;
      }
    } catch (Exception e) {
      validationErrors.append(
          String.format(
              "Failed to deploy resource '%s':\n",
              bufferAsString(deploymentResource.getResourceName())));
      validationErrors.append(generateErrorMessage(e));
      success = false;
    }
    return success;
  }

  private void transformWorkflowResource(
      DeploymentRecord deploymentEvent,
      DeploymentResource deploymentResource,
      BpmnModelInstance definition) {
    final Collection<Process> processes =
        definition.getDefinitions().getChildElementsByType(Process.class);

    for (Process workflow : processes) {
      if (workflow.isExecutable()) {
        final String bpmnProcessId = workflow.getId();
        final long key = index.getNextKey();
        final int version = index.getNextVersion(bpmnProcessId);

        deploymentEvent
            .deployedWorkflows()
            .add()
            .setBpmnProcessId(BufferUtil.wrapString(workflow.getId()))
            .setVersion(version)
            .setKey(key)
            .setResourceName(deploymentResource.getResourceName());
      }
    }

    transformYamlWorkflowResource(deploymentResource, definition);
  }

  private BpmnModelInstance readWorkflowDefinition(DeploymentResource deploymentResource) {
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

  private String generateErrorMessage(final Exception e) {
    final StringWriter stacktraceWriter = new StringWriter();

    e.printStackTrace(new PrintWriter(stacktraceWriter));

    return stacktraceWriter.toString();
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }
}
