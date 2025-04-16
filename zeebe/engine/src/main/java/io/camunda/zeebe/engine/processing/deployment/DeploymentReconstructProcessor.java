/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.deployment.DeploymentReconstructProcessor.Resource.DecisionRequirementsResource;
import io.camunda.zeebe.engine.processing.deployment.DeploymentReconstructProcessor.Resource.FormResource;
import io.camunda.zeebe.engine.processing.deployment.DeploymentReconstructProcessor.Resource.ProcessResource;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeploymentResourceUtil;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.DecisionState.DecisionRequirementsIdentifier;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.FormState.FormIdentifier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceIdentifier;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord.ReconstructionProgress;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;

/**
 * NOTE: DeploymentRecord is not actually used in the reconstruction, but there is a 1 <-> 1 mapping
 * between ValueType and RecordValue. The record is mutable so it's better to not reuse a
 * pre-allocated static instance.
 */
@ExcludeAuthorizationCheck
public class DeploymentReconstructProcessor implements TypedRecordProcessor<DeploymentRecord> {
  private static final long NO_DEPLOYMENT_KEY = -1;
  private final DeploymentResourceUtil resourceUtil = new DeploymentResourceUtil();
  private final KeyGenerator keyGenerator;
  private final DeploymentState deploymentState;
  private final ProcessState processState;
  private final FormState formState;
  private final DecisionState decisionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedCommandWriter commandWriter;
  private final DeploymentRecord cachedDeploymentRecordCommand = new DeploymentRecord();

  public DeploymentReconstructProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    deploymentState = processingState.getDeploymentState();
    processState = processingState.getProcessState();
    formState = processingState.getFormState();
    decisionState = processingState.getDecisionState();
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> record) {
    if (deploymentState.hasStoredAllDeployments()) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.ALREADY_EXISTS,
          "Deployments are already stored and don't need to be reconstructed");
      return;
    }
    final var identifier = fromDeploymentRecord(record.getValue());

    final var key = keyGenerator.nextKey();

    final var resource =
        findNextResource(identifier, record.getValue().getReconstructionProgress());
    if (resource == null) {
      stateWriter.appendFollowUpEvent(key, DeploymentIntent.RECONSTRUCTED_ALL, record.getValue());
      return;
    }

    final DeploymentRecord deploymentRecord;
    if (resource.deploymentKey() != NO_DEPLOYMENT_KEY) {
      final var allResourcesOfDeployment =
          findResourcesWithDeploymentKey(resource.tenantId(), resource.deploymentKey());
      deploymentRecord =
          recreateDeploymentForResources(
              resource.deploymentKey(), resource.tenantId(), allResourcesOfDeployment);
    } else {
      deploymentRecord = createNewDeploymentForResource(resource);
    }
    stateWriter.appendFollowUpEvent(
        deploymentRecord.getDeploymentKey(), DeploymentIntent.RECONSTRUCTED, deploymentRecord);
    // trigger reconstruction of another deployment reconstruction
    cachedDeploymentRecordCommand.reset();
    cachedDeploymentRecordCommand
        .setTenantId(deploymentRecord.getTenantId())
        .setReconstructionKey(deploymentRecord.getDeploymentKey())
        .setReconstructionProgress(Resource.progress(resource));
    commandWriter.appendNewCommand(DeploymentIntent.RECONSTRUCT, cachedDeploymentRecordCommand);
  }

  // Recursive function to find the next resource.
  // if the resource for the current type is not found, the next resource is tried.
  // If all the resources have been searched then it returns null.
  private Resource findNextResource(
      final ResourceIdentifier identifier, final ReconstructionProgress reconstructionProgress) {
    if (identifier == null & reconstructionProgress == ReconstructionProgress.DONE) {
      return null;
    }
    final var resource =
        switch (identifier) {
          case null ->
              switch (reconstructionProgress) {
                case PROCESS -> findProcessResource(null);
                case FORM -> findFormResource(null);
                case DECISION_REQUIREMENTS -> findDecisionRequirementsResource(null);
                case DONE -> null;
              };
          case final ProcessIdentifier processIdentifier -> findProcessResource(processIdentifier);
          case final FormIdentifier form -> findFormResource(form);
          case final DecisionRequirementsIdentifier decisionRequirementsIdentifier ->
              findDecisionRequirementsResource(decisionRequirementsIdentifier);
        };
    if (resource != null) {
      return resource;
    }
    return findNextResource(null, nextProgress(reconstructionProgress));
  }

  private ProcessResource findProcessResource(final ProcessIdentifier identifier) {
    final var foundProcess = new MutableReference<PersistedProcess>();

    processState.forEachProcess(
        identifier,
        process -> {
          final var deploymentKey = process.getDeploymentKey();
          if (deploymentKey != NO_DEPLOYMENT_KEY
              && deploymentState.hasStoredDeploymentRecord(deploymentKey)) {
            return true;
          }
          final var copy = new PersistedProcess();
          BufferUtil.copy(process, copy);
          foundProcess.set(copy);
          return false;
        });

    return foundProcess.get() != null ? new ProcessResource(foundProcess.get()) : null;
  }

  private FormResource findFormResource(final FormIdentifier identifier) {
    final var foundForm = new MutableReference<PersistedForm>();

    formState.forEachForm(
        identifier,
        form -> {
          final var deploymentKey = form.getDeploymentKey();
          if (deploymentKey != NO_DEPLOYMENT_KEY
              && deploymentState.hasStoredDeploymentRecord(deploymentKey)) {
            return true;
          }
          final var copy = new PersistedForm();
          BufferUtil.copy(form, copy);
          foundForm.set(copy);
          return false;
        });

    return foundForm.get() != null ? new FormResource(foundForm.get()) : null;
  }

  private DecisionRequirementsResource findDecisionRequirementsResource(
      final DecisionRequirementsIdentifier identifier) {
    final var foundDecisionRequirements = new MutableReference<PersistedDecisionRequirements>();
    final var foundDecisions = new MutableReference<Collection<PersistedDecision>>();
    final var foundDecisionDeploymentKey = new MutableLong(NO_DEPLOYMENT_KEY);

    decisionState.forEachDecisionRequirements(
        identifier,
        decisionRequirements -> {
          final var decisions =
              decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
                  decisionRequirements.getTenantId(),
                  decisionRequirements.getDecisionRequirementsKey());

          // DRGs are not associated with a deployment key, so we need to check if any of the
          // decisions are associated with a deployment key instead.
          final long deploymentKey =
              decisions.stream()
                  .map(PersistedDecision::getDeploymentKey)
                  .filter(key -> key != NO_DEPLOYMENT_KEY)
                  .findAny()
                  .orElse(NO_DEPLOYMENT_KEY);

          if (deploymentKey != NO_DEPLOYMENT_KEY
              && deploymentState.hasStoredDeploymentRecord(deploymentKey)) {
            return true;
          }

          final var copy = new PersistedDecisionRequirements();
          BufferUtil.copy(decisionRequirements, copy);
          foundDecisionRequirements.set(copy);
          foundDecisions.set(decisions);
          foundDecisionDeploymentKey.set(deploymentKey);
          return false;
        });

    final var decisionRequirements = foundDecisionRequirements.get();
    if (decisionRequirements != null) {
      return new DecisionRequirementsResource(
          foundDecisionDeploymentKey.get(), decisionRequirements, foundDecisions.get());
    } else {
      return null;
    }
  }

  private Set<Resource> findResourcesWithDeploymentKey(
      final String tenantId, final long deploymentKey) {
    // Iterate through processState, formState, and decisionState to find resources that are marked
    // with the given deployment key.
    final var resources = new HashSet<Resource>();
    processState.forEachProcess(
        // Start at the deployment key because any processes for that deployment will have a key
        // larger than that.
        new ProcessIdentifier(tenantId, deploymentKey),
        process -> {
          final var processDeploymentKey = process.getDeploymentKey();
          if (processDeploymentKey == deploymentKey) {
            final var copy = new PersistedProcess();
            BufferUtil.copy(process, copy);
            resources.add(new ProcessResource(copy));
          }
          // Stop searching when we arrived at a different tenant, as the processes are ordered by
          // tenant and we are guaranteed to have visited all processes for the given deployment
          // key.
          return process.getTenantId().equals(tenantId) && processDeploymentKey == deploymentKey;
        });

    formState.forEachForm(
        new FormIdentifier(tenantId, deploymentKey),
        form -> {
          final var formDeploymentKey = form.getDeploymentKey();
          if (formDeploymentKey == deploymentKey) {
            final var copy = new PersistedForm();
            BufferUtil.copy(form, copy);
            resources.add(new FormResource(copy));
          }
          return form.getTenantId().equals(tenantId) && formDeploymentKey == deploymentKey;
        });

    decisionState.forEachDecisionRequirements(
        new DecisionRequirementsIdentifier(tenantId, deploymentKey),
        decisionRequirements -> {
          final var decisions =
              decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
                  decisionRequirements.getTenantId(),
                  decisionRequirements.getDecisionRequirementsKey());
          if (decisions.stream()
              .anyMatch(decision -> decision.getDeploymentKey() == deploymentKey)) {
            resources.add(
                new DecisionRequirementsResource(
                    deploymentKey, decisionRequirements.copy(), decisions));
            return true;
          }
          return decisionRequirements.getTenantId().equals(tenantId);
        });

    return resources;
  }

  /**
   * Creates a new deployment for a given resource that was not marked with a deployment key. We
   * reuse the resource key as new deployment key.
   */
  private DeploymentRecord createNewDeploymentForResource(final Resource resource) {
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord.setDeploymentKey(resource.key());
    deploymentRecord.setTenantId(resource.tenantId());
    attachResourceMetadataToDeployment(deploymentRecord, resource);
    return deploymentRecord;
  }

  /**
   * Recreates a deployment for all resources that are already marked with a given deployment key.
   */
  private DeploymentRecord recreateDeploymentForResources(
      final long deploymentKey, final String tenantId, final Set<Resource> resources) {
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord.setDeploymentKey(deploymentKey);
    deploymentRecord.setTenantId(tenantId);
    for (final var resource : resources) {
      attachResourceMetadataToDeployment(deploymentRecord, resource);
    }
    return deploymentRecord;
  }

  private void attachResourceMetadataToDeployment(
      final DeploymentRecord deploymentRecord, final Resource resource) {
    switch (resource) {
      case ProcessResource(final var process) -> {
        final var metadata = deploymentRecord.processesMetadata().add();
        resourceUtil.applyProcessMetadata(process, metadata);
      }
      case FormResource(final var form) -> {
        final var metadata = deploymentRecord.formMetadata().add();
        resourceUtil.applyFormMetadata(form, metadata);
      }
      case DecisionRequirementsResource(
              final var deploymentKey,
              final var decisionRequirements,
              final var decisions) -> {
        final var requirementsMetadata = deploymentRecord.decisionRequirementsMetadata().add();
        resourceUtil.applyDecisionRequirementsMetadata(decisionRequirements, requirementsMetadata);
        decisions.forEach(
            decision -> {
              final var decisionMetadata = deploymentRecord.decisionsMetadata().add();
              resourceUtil.applyDecisionMetadata(decision, decisionMetadata);
            });
      }
    }
  }

  public static ReconstructionProgress nextProgress(final ReconstructionProgress progress) {
    return switch (progress) {
      case PROCESS -> ReconstructionProgress.FORM;
      case FORM -> ReconstructionProgress.DECISION_REQUIREMENTS;
      case DECISION_REQUIREMENTS, DONE -> ReconstructionProgress.DONE;
    };
  }

  static ResourceIdentifier fromDeploymentRecord(final DeploymentRecord record) {
    return switch (record.getReconstructionProgress()) {
      case PROCESS -> new ProcessIdentifier(record.getTenantId(), record.getReconstructionKey());
      case FORM -> new FormIdentifier(record.getTenantId(), record.getReconstructionKey());
      case DECISION_REQUIREMENTS ->
          new DecisionRequirementsIdentifier(record.getTenantId(), record.getReconstructionKey());
      case DONE -> null;
    };
  }

  sealed interface Resource {
    long key();

    long deploymentKey();

    String tenantId();

    ResourceIdentifier identifier();

    static ReconstructionProgress progress(final Resource resource) {
      return switch (resource) {
        case null -> ReconstructionProgress.DONE;
        case final DecisionRequirementsResource ignored ->
            ReconstructionProgress.DECISION_REQUIREMENTS;
        case final FormResource ignored -> ReconstructionProgress.FORM;
        case final ProcessResource ignored -> ReconstructionProgress.PROCESS;
      };
    }

    record ProcessResource(PersistedProcess process) implements Resource {

      @Override
      public long key() {
        return process.getKey();
      }

      @Override
      public long deploymentKey() {
        return process.getDeploymentKey();
      }

      @Override
      public String tenantId() {
        return process.getTenantId();
      }

      @Override
      public ResourceIdentifier identifier() {
        return process != null ? processIdentifier() : null;
      }

      public ProcessIdentifier processIdentifier() {
        return new ProcessIdentifier(tenantId(), key());
      }
    }

    record FormResource(PersistedForm form) implements Resource {

      @Override
      public long key() {
        return form.getFormKey();
      }

      @Override
      public long deploymentKey() {
        return form.getDeploymentKey();
      }

      @Override
      public String tenantId() {
        return form.getTenantId();
      }

      @Override
      public ResourceIdentifier identifier() {
        return form != null ? formIdentifier() : null;
      }

      public FormIdentifier formIdentifier() {
        return new FormIdentifier(tenantId(), form.getFormKey());
      }
    }

    record DecisionRequirementsResource(
        long deploymentKey,
        PersistedDecisionRequirements decisionRequirements,
        Collection<PersistedDecision> decisions)
        implements Resource {

      static DecisionRequirementsResource empty() {
        return new DecisionRequirementsResource(-1L, null, null);
      }

      @Override
      public long key() {
        return decisionRequirements.getDecisionRequirementsKey();
      }

      @Override
      public String tenantId() {
        return decisionRequirements.getTenantId();
      }

      @Override
      public ResourceIdentifier identifier() {
        return decisionRequirements != null ? decisionRequirementsIdentifier() : null;
      }

      DecisionRequirementsIdentifier decisionRequirementsIdentifier() {
        return new DecisionRequirementsIdentifier(tenantId(), key());
      }
    }
  }
}
