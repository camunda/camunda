/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

public final class FormResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;

  private static final long UNKNOWN_DECISION_REQUIREMENTS_KEY = -1L;

  private static final Either<Failure, Object> NO_DUPLICATES = Either.right(null);

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final Function<DeploymentResource, DirectBuffer> checksumGenerator;
  private final DecisionState decisionState;

  public FormResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final Function<DeploymentResource, DirectBuffer> checksumGenerator,
      final DecisionState decisionState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.decisionState = decisionState;
  }

  @Override
  public Either<Failure, Void> transformResource(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    final LongSupplier newFormKey = keyGenerator::nextKey;
    final DirectBuffer checksum = checksumGenerator.apply(resource);
    final var formRecord = deployment.formMetadata().add();

    final ObjectMapper mapper = new ObjectMapper();
    try {
      final String sResource = new String(resource.getResource(), StandardCharsets.UTF_8);
      final FormIdPOJO formIdPOJO = mapper.readValue(sResource, FormIdPOJO.class);
      formRecord
          .setFormId(formIdPOJO.getId())
          .setVersion(1) // TODO - set it properly
          .setFormKey(newFormKey.getAsLong())
          .setResourceName(resource.getResourceName())
          .setChecksum(checksum);

      stateWriter.appendFollowUpEvent(
          newFormKey.getAsLong(),
          FormIntent.CREATED,
          new FormRecord().wrap(formRecord, resource.getResource()));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return Either.right(null);
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
