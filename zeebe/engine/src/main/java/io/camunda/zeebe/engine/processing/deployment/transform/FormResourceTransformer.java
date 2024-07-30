/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.common.Failure;
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
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

public final class FormResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final Function<byte[], DirectBuffer> checksumGenerator;
  private final FormState formState;

  public FormResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final Function<byte[], DirectBuffer> checksumGenerator,
      final FormState formState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.formState = formState;
  }

  @Override
  public Either<Failure, Void> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    return parseFormId(resource)
        .flatMap(
            formId ->
                checkForDuplicateFormId(formId, resource, deployment)
                    .map(
                        noDuplicates -> {
                          final FormMetadataRecord formRecord = deployment.formMetadata().add();
                          appendMetadataToFormRecord(
                              formRecord, formId, resource, deployment.getTenantId());
                          return null;
                        }));
  }

  @Override
  public Either<Failure, Void> writeRecords(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return Either.right(null);
    }
    final var checksum = checksumGenerator.apply(resource.getResource());
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
                    .setDuplicate(false);
              }
              writeFormRecord(metadata, resource);
            });
    return Either.right(null);
  }

  private Either<Failure, String> parseFormId(final DeploymentResource resource) {
    try {
      final String formId = JSON_MAPPER.readValue(resource.getResource(), FormIdPOJO.class).getId();

      return validateFormId(formId);
    } catch (final JsonProcessingException e) {
      final var failureMessage =
          String.format(
              "Failed to parse formId from form JSON. '%s': %s",
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
        .orElse(Either.right(null));
  }

  private void appendMetadataToFormRecord(
      final FormMetadataRecord formRecord,
      final String formId,
      final DeploymentResource resource,
      final String tenantId) {
    final LongSupplier newFormKey = keyGenerator::nextKey;
    final DirectBuffer checksum = checksumGenerator.apply(resource.getResource());

    formRecord.setFormId(formId);
    formRecord.setChecksum(checksum);
    formRecord.setResourceName(resource.getResourceName());
    formRecord.setTenantId(tenantId);

    formState
        .findLatestFormById(wrapString(formRecord.getFormId()), tenantId)
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
                    .setDuplicate(true);
              } else {
                formRecord
                    .setFormKey(newFormKey.getAsLong())
                    .setVersion(formState.getNextFormVersion(formId, tenantId));
              }
            },
            () -> formRecord.setFormKey(newFormKey.getAsLong()).setVersion(INITIAL_VERSION));
  }

  private void writeFormRecord(
      final FormMetadataRecord formRecord, final DeploymentResource resource) {
    stateWriter.appendFollowUpEvent(
        formRecord.getFormKey(),
        FormIntent.CREATED,
        new FormRecord().wrap(formRecord, resource.getResource()));
  }

  private Either<Failure, String> validateFormId(final String formId) {
    if (formId == null) {
      return Either.left(new Failure("Expected the form id to be present, but none given"));
    }
    if (formId.isBlank()) {
      return Either.left(new Failure("Expected the form id to be filled, but it is blank"));
    }

    return Either.right(formId);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class FormIdPOJO {
    private String id;

    public FormIdPOJO() {}

    public String getId() {
      return id;
    }

    public void setId(final String id) {
      this.id = id;
    }
  }
}
