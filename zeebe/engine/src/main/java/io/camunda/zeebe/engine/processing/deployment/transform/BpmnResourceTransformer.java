/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.validation.StraightThroughProcessingLoopValidator;
import io.camunda.zeebe.engine.processing.deployment.model.validation.UnsupportedMultiTenantFeaturesValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
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
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.bpm.model.xml.ModelParseException;

public final class BpmnResourceTransformer implements DeploymentResourceTransformer {

  private final BpmnTransformer bpmnTransformer = BpmnFactory.createTransformer();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final Function<byte[], DirectBuffer> checksumGenerator;

  private final BpmnValidator validator;
  private final ProcessState processState;
  private final boolean enableStraightThroughProcessingLoopDetector;

  public BpmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final Function<byte[], DirectBuffer> checksumGenerator,
      final ProcessState processState,
      final ExpressionProcessor expressionProcessor,
      final boolean enableStraightThroughProcessingLoopDetector,
      final EngineConfiguration config) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.processState = processState;
    validator =
        BpmnFactory.createValidator(
            expressionProcessor, config.getValidatorsResultsOutputMaxSize());
    this.enableStraightThroughProcessingLoopDetector = enableStraightThroughProcessingLoopDetector;
  }

  @Override
  public Either<Failure, Void> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    return readProcessDefinition(resource)
        .flatMap(
            definition -> {
              final String validationError = validator.validate(definition);

              if (validationError == null) {
                // transform the model to avoid unexpected failures that are not covered by the
                // validator
                final var executableProcesses = bpmnTransformer.transformDefinitions(definition);

                return checkForDuplicateBpmnId(definition, resource, deployment)
                    .flatMap(
                        ok ->
                            UnsupportedMultiTenantFeaturesValidator.validate(
                                resource, executableProcesses, deployment.getTenantId()))
                    .flatMap(
                        ok -> {
                          if (enableStraightThroughProcessingLoopDetector) {
                            return StraightThroughProcessingLoopValidator.validate(
                                resource, executableProcesses);
                          }
                          return Either.right(null);
                        })
                    .map(
                        ok -> {
                          createProcessMetadata(deployment, resource, definition);
                          return null;
                        });

              } else {
                final var failureMessage =
                    String.format("'%s': %s", resource.getResourceName(), validationError);
                return Either.left(new Failure(failureMessage));
              }
            });
  }

  @Override
  public Either<Failure, Void> writeRecords(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return Either.right(null);
    }
    final var checksum = checksumGenerator.apply(resource.getResource());
    deployment.processesMetadata().stream()
        .filter(metadata -> checksum.equals(metadata.getChecksumBuffer()))
        .forEach(
            metadata -> {
              var key = metadata.getKey();
              if (metadata.isDuplicate()) {
                // create new version as the deployment contains at least one other non-duplicate
                // resource and all resources in a deployment should be versioned together
                key = keyGenerator.nextKey();
                metadata
                    .setKey(key)
                    .setVersion(
                        processState.getNextProcessVersion(
                            metadata.getBpmnProcessId(), deployment.getTenantId()))
                    .setDuplicate(false);
              }
              stateWriter.appendFollowUpEvent(
                  key,
                  ProcessIntent.CREATED,
                  new ProcessRecord().wrap(metadata, resource.getResource()));
            });
    return Either.right(null);
  }

  private Either<Failure, BpmnModelInstance> readProcessDefinition(
      final DeploymentResource deploymentResource) {
    try {
      final DirectBuffer resource = deploymentResource.getResourceBuffer();
      final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);
      return Either.right(Bpmn.readModelFromStream(resourceStream));
    } catch (final ModelParseException e) {
      final var failureMessage =
          String.format(
              "'%s': %s", deploymentResource.getResourceName(), e.getCause().getMessage());
      return Either.left(new Failure(failureMessage));
    }
  }

  private Either<Failure, ?> checkForDuplicateBpmnId(
      final BpmnModelInstance process,
      final DeploymentResource resource,
      final DeploymentRecord record) {

    final var bpmnProcessIds =
        process.getDefinitions().getChildElementsByType(Process.class).stream()
            .map(BaseElement::getId)
            .toList();

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

  private void createProcessMetadata(
      final DeploymentRecord deploymentEvent,
      final DeploymentResource deploymentResource,
      final BpmnModelInstance definition) {
    for (final Process process : getExecutableProcesses(definition)) {
      final String bpmnProcessId = process.getId();
      final String tenantId = deploymentEvent.getTenantId();
      final DeployedProcess lastProcess =
          processState.getLatestProcessVersionByProcessId(
              BufferUtil.wrapString(bpmnProcessId), tenantId);

      final DirectBuffer lastDigest =
          processState.getLatestVersionDigest(wrapString(bpmnProcessId), tenantId);
      final DirectBuffer resourceDigest = checksumGenerator.apply(deploymentResource.getResource());

      // adds process record to deployment record
      final var processMetadata = deploymentEvent.processesMetadata().add();
      processMetadata
          .setBpmnProcessId(BufferUtil.wrapString(bpmnProcessId))
          .setChecksum(resourceDigest)
          .setResourceName(deploymentResource.getResourceNameBuffer())
          .setTenantId(tenantId);

      final var isDuplicate =
          isDuplicateOfLatest(deploymentResource, resourceDigest, lastProcess, lastDigest);
      if (isDuplicate) {
        processMetadata
            .setVersion(lastProcess.getVersion())
            .setKey(lastProcess.getKey())
            .setDuplicate(true);
      } else {
        processMetadata
            .setKey(keyGenerator.nextKey())
            .setVersion(processState.getNextProcessVersion(bpmnProcessId, tenantId));
      }
    }
  }

  private List<Process> getExecutableProcesses(final BpmnModelInstance modelInstance) {
    return modelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
        .filter(Process::isExecutable)
        .toList();
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
