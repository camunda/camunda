/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

public final class FormResourceTransformer implements DeploymentResourceTransformer {
  private static final Either<Failure, Object> NO_VALIDATION_ERROR = Either.right(null);

  private static final int INITIAL_VERSION = 1;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ChecksumGenerator checksumGenerator;
  private final FormState formState;
  private final EngineConfiguration config;

  public FormResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final FormState formState,
      final EngineConfiguration config) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.formState = formState;
    this.config = config;
  }

  @Override
  public Either<Failure, Void> createMetadata(
      final DeploymentResource resource,
      final DeploymentRecord deployment,
      final DeploymentResourceContext context) {
    return parseForm(resource)
        .flatMap(
            form ->
                checkForDuplicateFormId(form.id, resource, deployment)
                    .flatMap(valid -> checkForFormIdLength(form.id, resource))
                    .map(
                        valid -> {
                          final FormMetadataRecord formRecord = deployment.formMetadata().add();
                          appendMetadataToFormRecord(formRecord, form, resource, deployment);
                          return null;
                        }));
  }

  @Override
  public void writeRecords(final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return;
    }
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
    deployment.formMetadata().stream()
        .filter(metadata -> checksum.equals(metadata.getChecksumBuffer()))
        .findFirst()
        .ifPresent(
            metadata -> {
              var key = metadata.getFormKey();
              if (metadata.isDuplicate()) {
                // create new version as the deployment contains at least one other non-duplicate
                // resource and all resources in a deployment should be versioned together
                key = keyGenerator.nextKey();
                metadata
                    .setFormKey(key)
                    .setVersion(
                        formState.getNextFormVersion(metadata.getFormId(), metadata.getTenantId()))
                    .setDuplicate(false)
                    .setDeploymentKey(deployment.getDeploymentKey());
              }
              writeFormRecord(metadata, resource);
            });
  }

  private Either<Failure, Form> parseForm(final DeploymentResource resource) {
    try {
      final var form = JSON_MAPPER.readValue(resource.getResource(), Form.class);
      return validateForm(form);
    } catch (final JsonProcessingException e) {
      final var failureMessage =
          String.format(
              "Failed to parse form JSON. '%s': %s",
              resource.getResourceName(), e.getCause().getMessage());
      return Either.left(new Failure(failureMessage));
    } catch (final IOException e) {
      final var failureMessage =
          String.format("'%s': %s", resource.getResourceName(), e.getCause().getMessage());
      return Either.left(new Failure(failureMessage));
    }
  }

  private Either<Failure, ?> checkForDuplicateFormId(
      final String formId, final DeploymentResource resource, final DeploymentRecord record) {
    return record.getFormMetadata().stream()
        .filter(metadata -> metadata.getFormId().equals(formId))
        .findFirst()
        .map(
            duplicatedForm -> {
              final var failureMessage =
                  String.format(
                      "Expected the form ids to be unique within a deployment"
                          + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                      formId, duplicatedForm.getResourceName(), resource.getResourceName());
              return Either.left(new Failure(failureMessage));
            })
        .orElse(NO_VALIDATION_ERROR);
  }

  private Either<Failure, ?> checkForFormIdLength(
      final String formId, final DeploymentResource resource) {

    if (formId != null && formId.length() > config.getMaxIdFieldLength()) {
      final var failureMessage =
          String.format(
              "The ID of a form must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
              config.getMaxIdFieldLength(), formId, resource.getResourceName());
      return Either.left(new Failure(failureMessage));
    }

    return NO_VALIDATION_ERROR;
  }

  private void appendMetadataToFormRecord(
      final FormMetadataRecord formRecord,
      final Form form,
      final DeploymentResource resource,
      final DeploymentRecord deployment) {
    final LongSupplier newFormKey = keyGenerator::nextKey;
    final DirectBuffer checksum = checksumGenerator.checksum(resource.getResourceBuffer());
    final String tenantId = deployment.getTenantId();

    formRecord.setFormId(form.id);
    formRecord.setChecksum(checksum);
    formRecord.setResourceName(resource.getResourceName());
    formRecord.setTenantId(tenantId);
    Optional.ofNullable(form.versionTag).ifPresent(formRecord::setVersionTag);

    formState
        .findLatestFormById(formRecord.getFormId(), tenantId)
        .ifPresentOrElse(
            latestForm -> {
              final boolean isDuplicate =
                  latestForm.getChecksum().equals(formRecord.getChecksumBuffer())
                      && latestForm.getResourceName().equals(formRecord.getResourceNameBuffer());

              if (isDuplicate) {
                final int latestVersion = latestForm.getVersion();
                formRecord
                    .setFormKey(latestForm.getFormKey())
                    .setVersion(latestVersion)
                    .setDeploymentKey(latestForm.getDeploymentKey())
                    .setDuplicate(true);
              } else {
                formRecord
                    .setFormKey(newFormKey.getAsLong())
                    .setVersion(formState.getNextFormVersion(form.id, tenantId))
                    .setDeploymentKey(deployment.getDeploymentKey());
              }
            },
            () ->
                formRecord
                    .setFormKey(newFormKey.getAsLong())
                    .setVersion(INITIAL_VERSION)
                    .setDeploymentKey(deployment.getDeploymentKey()));
  }

  private void writeFormRecord(
      final FormMetadataRecord formRecord, final DeploymentResource resource) {
    stateWriter.appendFollowUpEvent(
        formRecord.getFormKey(),
        FormIntent.CREATED,
        new FormRecord().wrap(formRecord, resource.getResource()));
  }

  private Either<Failure, Form> validateForm(final Form form) {
    if (form.id == null) {
      return Either.left(new Failure("Expected the form id to be present, but none given"));
    }
    if (form.id.isBlank()) {
      return Either.left(new Failure("Expected the form id to be filled, but it is blank"));
    }
    return Either.right(form);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Form(String id, String versionTag) {}
}
