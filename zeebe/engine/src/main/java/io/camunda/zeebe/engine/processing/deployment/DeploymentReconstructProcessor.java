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
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
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
    if (record.getIntent() != DeploymentIntent.RECONSTRUCT) {
      throw new IllegalArgumentException(
          "Expected a record with intent DeploymentIntent.RECONSTRUCT, but got "
              + record.getIntent());
    }
    if (deploymentState.hasStoredAllDeployments()) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.ALREADY_EXISTS,
          "Deployments are already stored and don't need to be reconstructed");
      return;
    }

    final var key = keyGenerator.nextKey();
    final var resource = findNextResource();
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
    // TODO: is there a "safe" way to avoid allocating a new DeploymentRecord each time?
    commandWriter.appendNewCommand(DeploymentIntent.RECONSTRUCT, new DeploymentRecord());
  }

  private Resource findNextResource() {
    return findProcessResource()
        .or(this::findFormResource)
        .or(this::findDecisionRequirementsResource)
        .orElse(null);
  }

  private Optional<Resource> findProcessResource() {
    final var foundProcess = new MutableReference<PersistedProcess>();

    processState.forEachProcess(
        null, // TODO: Continue where we left off
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

    return Optional.ofNullable(foundProcess.get()).map(ProcessResource::new);
  }

  private Optional<Resource> findFormResource() {
    final var foundForm = new MutableReference<PersistedForm>();

    formState.forEachForm(
        null, // TODO: Continue where we left off
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

    return Optional.ofNullable(foundForm.get()).map(FormResource::new);
  }

  private Optional<Resource> findDecisionRequirementsResource() {
    final var foundDecisionRequirements = new MutableReference<PersistedDecisionRequirements>();
    final var foundDecisions = new MutableReference<Collection<PersistedDecision>>();
    final var foundDecisionDeploymentKey = new MutableLong(NO_DEPLOYMENT_KEY);

    decisionState.forEachDecisionRequirements(
        null, // TODO: Continue where we left off
        decisionRequirements -> {
          final var decisions =
              decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
                  decisionRequirements.getTenantId(),
                  decisionRequirements.getDecisionRequirementsKey());

          // DRGs are not associated with a deployment key, so we need to check if any of the
          // decisions are associated with a deployment key instead.
          final var deploymentKey =
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

    return Optional.ofNullable(foundDecisionRequirements.get())
        .map(
            decisionRequirements ->
                new DecisionRequirementsResource(
                    foundDecisionDeploymentKey.get(), decisionRequirements, foundDecisions.get()));
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

  sealed interface Resource {
    long key();

    long deploymentKey();

    String tenantId();

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
    }

    record DecisionRequirementsResource(
        long deploymentKey,
        PersistedDecisionRequirements decisionRequirements,
        Collection<PersistedDecision> decisions)
        implements Resource {

      @Override
      public long key() {
        return decisionRequirements.getDecisionRequirementsKey();
      }

      @Override
      public String tenantId() {
        return decisionRequirements.getTenantId();
      }
    }
  }
}
