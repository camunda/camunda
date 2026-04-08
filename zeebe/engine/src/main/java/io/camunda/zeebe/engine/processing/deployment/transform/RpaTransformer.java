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
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.Optional;

public class RpaTransformer extends DefaultResourceTransformer {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  public RpaTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ResourceState resourceState) {
    super(keyGenerator, stateWriter, checksumGenerator, resourceState);
  }

  @Override
  public boolean canTransform(final DeploymentResource resource) {
    return resource.getResourceName().endsWith(".rpa");
  }

  @Override
  protected Either<Failure, ResourceId> parseResourceId(final DeploymentResource resource) {
    try {
      final var rpaResource =
          JSON_MAPPER.readValue(resource.getResource(), ParsedRpaResource.class);
      return validateRpaResource(rpaResource)
          .map(valid -> ResourceId.of(valid.id, valid.versionTag));
    } catch (final JsonProcessingException e) {
      final var failureMessage =
          String.format(
              "Failed to parse resource JSON. '%s': %s",
              resource.getResourceName(), getFailureExceptionMessage(e));
      return Either.left(new Failure(failureMessage));
    } catch (final IOException e) {
      final var failureMessage =
          String.format("'%s': %s", resource.getResourceName(), getFailureExceptionMessage(e));
      return Either.left(new Failure(failureMessage));
    }
  }

  private static String getFailureExceptionMessage(final Exception exception) {
    return Optional.ofNullable(exception.getCause())
        .map(Throwable::getMessage)
        .orElse(exception.getMessage());
  }

  private Either<Failure, ParsedRpaResource> validateRpaResource(final ParsedRpaResource res) {
    if (res.id == null) {
      return Either.left(new Failure("Expected the resource id to be present, but none given"));
    }
    if (res.id.isBlank()) {
      return Either.left(new Failure("Expected the resource id to be filled, but it is blank"));
    }
    return Either.right(res);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ParsedRpaResource(String id, String versionTag) {}
}
