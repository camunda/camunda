/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.transform;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.util.buffer.BufferUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  private final BpmnValidator validator;
  private final MutableWorkflowState workflowState;
  private final KeyGenerator keyGenerator;
  private final MessageDigest digestGenerator;
  // process id duplicate checking
  private final Map<String, String> processIdToResourceName = new HashMap<>();
  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentTransformer(
      final ZeebeState zeebeState, final ExpressionProcessor expressionProcessor) {
    workflowState = zeebeState.getWorkflowState();
    keyGenerator = zeebeState.getKeyGenerator();
    validator = BpmnFactory.createValidator(expressionProcessor);

    try {
      // We get an alert by LGTM, since MD5 is a weak cryptographic hash function,
      // but it is not easy to exchange this weak algorithm without getting compatibility issues
      // with previous versions. Furthermore it is very unlikely that we get problems on checking
      // the deployments hashes.
      digestGenerator =
          MessageDigest.getInstance("MD5"); // lgtm [java/weak-cryptographic-algorithm]
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean transform(final DeploymentRecord deploymentEvent) {
    final StringBuilder errors = new StringBuilder();
    boolean success = true;
    processIdToResourceName.clear();

    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
    if (!resourceIterator.hasNext()) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = "Expected to deploy at least one resource, but none given";
      return false;
    }

    while (resourceIterator.hasNext()) {
      final DeploymentResource deploymentResource = resourceIterator.next();
      success &= transformResource(deploymentEvent, errors, deploymentResource);
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s",
              errors.toString());
    }

    return success;
  }

  private boolean transformResource(
      final DeploymentRecord deploymentEvent,
      final StringBuilder errors,
      final DeploymentResource deploymentResource) {
    boolean success = false;
    final String resourceName = deploymentResource.getResourceName();

    try {
      final BpmnModelInstance definition = readWorkflowDefinition(deploymentResource);
      final String validationError = validator.validate(definition);

      if (validationError == null) {
        // transform the model to avoid unexpected failures that are not covered by the validator
        BpmnFactory.createTransformer().transformDefinitions(definition);

        final String bpmnIdDuplicateError = checkForDuplicateBpmnId(definition, resourceName);

        if (bpmnIdDuplicateError == null) {
          transformWorkflowResource(deploymentEvent, deploymentResource, definition);
          success = true;
        } else {
          errors.append("\n").append(bpmnIdDuplicateError);
        }
      } else {
        errors.append("\n'").append(resourceName).append("': ").append(validationError);
      }
    } catch (final RuntimeException e) {
      LOG.error("Unexpected error while processing resource '{}'", resourceName, e);
      errors.append("\n'").append(resourceName).append("': ").append(e.getMessage());
    }
    return success;
  }

  private String checkForDuplicateBpmnId(
      final BpmnModelInstance model, final String currentResource) {
    final Collection<Process> processes =
        model.getDefinitions().getChildElementsByType(Process.class);

    for (final Process process : processes) {
      final String previousResource = processIdToResourceName.get(process.getId());
      if (previousResource != null) {
        return String.format(
            "Duplicated process id in resources '%s' and '%s'", previousResource, currentResource);
      }

      processIdToResourceName.put(process.getId(), currentResource);
    }

    return null;
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
        final DeployedWorkflow lastWorkflow =
            workflowState.getLatestWorkflowVersionByProcessId(BufferUtil.wrapString(bpmnProcessId));
        final long key;
        final int version;

        final DirectBuffer lastDigest =
            workflowState.getLatestVersionDigest(wrapString(bpmnProcessId));
        final DirectBuffer resourceDigest =
            new UnsafeBuffer(digestGenerator.digest(deploymentResource.getResource()));

        if (isDuplicateOfLatest(deploymentResource, resourceDigest, lastWorkflow, lastDigest)) {
          key = lastWorkflow.getKey();
          version = lastWorkflow.getVersion();
        } else {
          key = keyGenerator.nextKey();
          version = workflowState.getNextWorkflowVersion(bpmnProcessId);
          workflowState.putLatestVersionDigest(wrapString(bpmnProcessId), resourceDigest);
        }

        deploymentEvent
            .workflows()
            .add()
            .setBpmnProcessId(BufferUtil.wrapString(workflow.getId()))
            .setVersion(version)
            .setKey(key)
            .setResourceName(deploymentResource.getResourceNameBuffer());
      }
    }
  }

  private boolean isDuplicateOfLatest(
      final DeploymentResource deploymentResource,
      final DirectBuffer resourceDigest,
      final DeployedWorkflow lastWorkflow,
      final DirectBuffer lastVersionDigest) {
    return lastVersionDigest != null
        && lastWorkflow != null
        && lastVersionDigest.equals(resourceDigest)
        && lastWorkflow.getResourceName().equals(deploymentResource.getResourceNameBuffer());
  }

  private BpmnModelInstance readWorkflowDefinition(final DeploymentResource deploymentResource) {
    final DirectBuffer resource = deploymentResource.getResourceBuffer();
    final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);
    return Bpmn.readModelFromStream(resourceStream);
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }
}
