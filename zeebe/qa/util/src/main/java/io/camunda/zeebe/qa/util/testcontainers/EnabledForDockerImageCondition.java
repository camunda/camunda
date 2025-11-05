/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

public final class EnabledForDockerImageCondition implements ExecutionCondition, BooleanSupplier {
  private static final Class<EnabledIfDockerImageExists> ANNOTATION_TYPE =
      EnabledIfDockerImageExists.class;
  private static final String DEFAULT_DISABLED_REASON =
      "Docker image [%s] is not available on the local system";
  private static final String DEFAULT_ENABLED_REASON =
      "Docker image [%s] is available on the local system";

  private String dockerImageName;
  private String disabledReason;

  public EnabledForDockerImageCondition() {
    this("");
  }

  public EnabledForDockerImageCondition(final String dockerImageName) {
    this(dockerImageName, "");
  }

  public EnabledForDockerImageCondition(final String dockerImageName, final String disabledReason) {
    this.dockerImageName = Objects.requireNonNull(dockerImageName);
    this.disabledReason = Objects.requireNonNull(disabledReason);
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return findAnnotation(context.getElement(), ANNOTATION_TYPE) //
        .map(annotation -> verifyCondition(annotation.value(), annotation.disabledReason()))
        .orElseGet(() -> verifyCondition(dockerImageName, disabledReason));
  }

  @Override
  public boolean getAsBoolean() {
    return false;
  }

  private @NotNull ConditionEvaluationResult verifyCondition(
      final String dockerImageName, final String disabledReason) {
    final var parsedImageName = DockerImageName.parse(dockerImageName);
    final var customDisabledReason =
        disabledReason == null ? "" : disabledReason.formatted(parsedImageName);

    return isAvailable(parsedImageName)
        ? enabled(DEFAULT_ENABLED_REASON.formatted(parsedImageName))
        : disabled(DEFAULT_DISABLED_REASON.formatted(parsedImageName), customDisabledReason);
  }

  private ConditionEvaluationResult enabledByDefault() {
    String reason = String.format("@%s is not present", ANNOTATION_TYPE.getSimpleName());
    return enabled(reason);
  }

  private boolean isAvailable(final DockerImageName dockerImageName) {
    final var remoteImage =
        new RemoteDockerImage(dockerImageName).withImagePullPolicy(PullPolicy.defaultPolicy());
    try {
      final var result = remoteImage.get(5, TimeUnit.MINUTES);
      return true;
    } catch (final Exception e) {
      return false;
    }
  }
}
