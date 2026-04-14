/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.validation.StraightThroughProcessingLoopValidator;
import io.camunda.zeebe.engine.processing.deployment.model.validation.UnsupportedMultiTenantFeaturesValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeVersionTag;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.bpm.model.xml.ModelParseException;

public final class BpmnResourceTransformer implements DeploymentResourceTransformer {

  private final BpmnTransformer bpmnTransformer;

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ChecksumGenerator checksumGenerator;

  private final BpmnValidator validator;
  private final ProcessState processState;
  private final boolean enableStraightThroughProcessingLoopDetector;
  private final BpmnElementOrderValidator elementOrderValidator;

  public BpmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ProcessState processState,
      final ExpressionProcessor expressionProcessor,
      final boolean enableStraightThroughProcessingLoopDetector,
      final ValidationConfig config,
      final InstantSource clock,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
    bpmnTransformer =
        BpmnFactory.createTransformer(
            clock, expressionLanguageMetrics, config.maxNameFieldLength());
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.processState = processState;
    validator =
        BpmnFactory.createValidator(clock, expressionProcessor, config, expressionLanguageMetrics);
    this.enableStraightThroughProcessingLoopDetector = enableStraightThroughProcessingLoopDetector;
    elementOrderValidator = new BpmnElementOrderValidator();
  }

  @Override
  public boolean canTransform(final DeploymentResource resource) {
    final var resourceName = resource.getResourceName();
    // .bpmn files must always be handled by this transformer (even if invalid)
    if (resourceName.endsWith(".bpmn")) {
      return true;
    }
    // .xml files: try to parse as BPMN and only handle if it's valid BPMN.
    // Non-BPMN .xml files fall through to the default transformer (generic resource).
    if (resourceName.endsWith(".xml")) {
      return readProcessDefinition(resource).isRight();
    }
    return false;
  }

  @Override
  public Either<Failure, DeploymentResourceContext> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    return readProcessDefinition(resource)
        .flatMap(
            definition -> {
              final String validationError = validator.validate(definition);

              if (validationError == null) {
                // transform the model to avoid unexpected failures that are not covered by the
                // validator
                final var executableProcesses = bpmnTransformer.transformDefinitions(definition);

                return UnsupportedMultiTenantFeaturesValidator.validate(
                        resource, executableProcesses, deployment.getTenantId())
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
                          final var elements =
                              new BpmnElementsWithDeploymentBinding(resource.getResourceName());
                          createProcessMetadata(deployment, resource, definition, elements);
                          return (DeploymentResourceContext) elements;
                        });

              } else {
                final var failureMessage =
                    String.format("'%s': %s", resource.getResourceName(), validationError);
                return Either.left(new Failure(failureMessage));
              }
            });
  }

  @Override
  public void writeRecords(final DeploymentResource resource, final DeploymentRecord deployment) {
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
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
                    .setDuplicate(false)
                    .setDeploymentKey(deployment.getDeploymentKey());
              }
              stateWriter.appendFollowUpEvent(
                  key,
                  ProcessIntent.CREATED,
                  new ProcessRecord().wrap(metadata, resource.getResource()));
            });
  }

  private Either<Failure, BpmnModelInstance> readProcessDefinition(
      final DeploymentResource deploymentResource) {
    try {
      final DirectBuffer resource = deploymentResource.getResourceBuffer();
      final DirectBufferInputStream resourceStream = new DirectBufferInputStream(resource);
      return Either.right(Bpmn.readModelFromStream(resourceStream));
    } catch (final ModelParseException e) {
      final var errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      final var improvedMessage =
          elementOrderValidator.improveElementOrderingErrorMessage(errorMessage);
      final var failureMessage =
          String.format("'%s': %s", deploymentResource.getResourceName(), improvedMessage);
      return Either.left(new Failure(failureMessage));
    }
  }

  private void createProcessMetadata(
      final DeploymentRecord deploymentEvent,
      final DeploymentResource deploymentResource,
      final BpmnModelInstance definition,
      final BpmnElementsWithDeploymentBinding elements) {
    for (final Process process : getExecutableProcesses(definition)) {
      final String bpmnProcessId = process.getId();
      final String tenantId = deploymentEvent.getTenantId();
      final DeployedProcess lastProcess =
          processState.getLatestProcessVersionByProcessId(
              BufferUtil.wrapString(bpmnProcessId), tenantId);

      final DirectBuffer lastDigest =
          processState.getLatestVersionDigest(wrapString(bpmnProcessId), tenantId);
      final DirectBuffer resourceDigest =
          checksumGenerator.checksum(deploymentResource.getResourceBuffer());

      // adds process record to deployment record
      final var processMetadata = deploymentEvent.processesMetadata().add();
      processMetadata
          .setBpmnProcessId(BufferUtil.wrapString(bpmnProcessId))
          .setChecksum(resourceDigest)
          .setResourceName(deploymentResource.getResourceNameBuffer())
          .setTenantId(tenantId);
      getOptionalVersionTag(process).ifPresent(processMetadata::setVersionTag);

      final var isDuplicate =
          isDuplicateOfLatest(deploymentResource, resourceDigest, lastProcess, lastDigest);
      if (isDuplicate) {
        processMetadata
            .setKey(lastProcess.getKey())
            .setVersion(lastProcess.getVersion())
            .setDeploymentKey(lastProcess.getDeploymentKey())
            .setDuplicate(true);
      } else {
        processMetadata
            .setKey(keyGenerator.nextKey())
            .setVersion(processState.getNextProcessVersion(bpmnProcessId, tenantId))
            .setDeploymentKey(deploymentEvent.getDeploymentKey());
      }

      elements.addFromProcess(process);
    }
  }

  private List<Process> getExecutableProcesses(final BpmnModelInstance modelInstance) {
    return modelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
        .filter(Process::isExecutable)
        .toList();
  }

  private Optional<String> getOptionalVersionTag(final Process process) {
    return Optional.ofNullable(process.getSingleExtensionElement(ZeebeVersionTag.class))
        .map(ZeebeVersionTag::getValue);
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
