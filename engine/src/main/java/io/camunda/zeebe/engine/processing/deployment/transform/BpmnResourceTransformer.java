/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class BpmnResourceTransformer implements DeploymentResourceTransformer {

  private final BpmnTransformer bpmnTransformer = BpmnFactory.createTransformer();

  private final KeyGenerator keyGenerator;
  private final StateBuilder stateBuilder;
  private final Function<DeploymentResource, DirectBuffer> checksumGenerator;

  private final BpmnValidator validator;
  private final ProcessState processState;

  public BpmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateBuilder stateBuilder,
      final Function<DeploymentResource, DirectBuffer> checksumGenerator,
      final ProcessState processState,
      final ExpressionProcessor expressionProcessor) {
    this.keyGenerator = keyGenerator;
    this.stateBuilder = stateBuilder;
    this.checksumGenerator = checksumGenerator;
    this.processState = processState;
    validator = BpmnFactory.createValidator(expressionProcessor);
  }

  @Override
  public Either<Failure, Void> transformResource(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    final BpmnModelInstance definition = readProcessDefinition(resource);
    final String validationError = validator.validate(definition);

    if (validationError == null) {
      // transform the model to avoid unexpected failures that are not covered by the validator
      bpmnTransformer.transformDefinitions(definition);

      return checkForDuplicateBpmnId(definition, resource, deployment)
          .map(
              ok -> {
                transformProcessResource(deployment, resource, definition);
                return null;
              });

    } else {
      final var failureMessage =
          String.format("'%s': %s", resource.getResourceName(), validationError);
      return Either.left(new Failure(failureMessage));
    }
  }

  private BpmnModelInstance readProcessDefinition(final DeploymentResource deploymentResource) {
    final DirectBuffer resource = deploymentResource.getResourceBuffer();
    final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);
    return Bpmn.readModelFromStream(resourceStream);
  }

  private Either<Failure, ?> checkForDuplicateBpmnId(
      final BpmnModelInstance process,
      final DeploymentResource resource,
      final DeploymentRecord record) {

    final var bpmnProcessIds =
        process.getDefinitions().getChildElementsByType(Process.class).stream()
            .map(BaseElement::getId)
            .collect(Collectors.toList());

    return record.getProcessesMetadata().stream()
        .filter(metadata -> bpmnProcessIds.contains(metadata.getBpmnProcessId()))
        .findFirst()
        .map(
            previousResource -> {
              final var failureMessage =
                  String.format(
                      "Duplicated process id in resources '%s' and '%s'",
                      previousResource.getResourceName(), resource.getResourceName());
              return Either.left(new Failure(failureMessage));
            })
        .orElse(Either.right(null));
  }

  private void transformProcessResource(
      final DeploymentRecord deploymentEvent,
      final DeploymentResource deploymentResource,
      final BpmnModelInstance definition) {
    final Collection<Process> processes =
        definition.getDefinitions().getChildElementsByType(Process.class);

    for (final Process process : processes) {
      if (process.isExecutable()) {
        final String bpmnProcessId = process.getId();
        final DeployedProcess lastProcess =
            processState.getLatestProcessVersionByProcessId(BufferUtil.wrapString(bpmnProcessId));

        final DirectBuffer lastDigest =
            processState.getLatestVersionDigest(wrapString(bpmnProcessId));
        final DirectBuffer resourceDigest = checksumGenerator.apply(deploymentResource);

        // adds process record to deployment record
        final var processMetadata = deploymentEvent.processesMetadata().add();
        processMetadata
            .setBpmnProcessId(BufferUtil.wrapString(process.getId()))
            .setChecksum(resourceDigest)
            .setResourceName(deploymentResource.getResourceNameBuffer());

        final var isDuplicate =
            isDuplicateOfLatest(deploymentResource, resourceDigest, lastProcess, lastDigest);
        if (isDuplicate) {
          processMetadata
              .setVersion(lastProcess.getVersion())
              .setKey(lastProcess.getKey())
              .markAsDuplicate();
        } else {
          final var key = keyGenerator.nextKey();
          processMetadata.setKey(key).setVersion(processState.getProcessVersion(bpmnProcessId) + 1);

          stateBuilder.appendFollowUpEvent(
              key,
              ProcessIntent.CREATED,
              new ProcessRecord().wrap(processMetadata, deploymentResource.getResource()));
        }
      }
    }
  }

  private boolean isDuplicateOfLatest(
      final DeploymentResource deploymentResource,
      final DirectBuffer resourceDigest,
      final DeployedProcess lastProcess,
      final DirectBuffer lastVersionDigest) {
    return lastVersionDigest != null
        && lastProcess != null
        && lastVersionDigest.equals(resourceDigest)
        && lastProcess.getResourceName().equals(deploymentResource.getResourceNameBuffer());
  }
}
