/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.HashMap;
import java.util.Map;

public class ClusterConfigurationRequestValidators {

  private final Map<
          Class<? extends ClusterConfigurationManagementRequest>,
          ClusterConfigurationRequestValidator<? extends ClusterConfigurationManagementRequest>>
      validators = new HashMap<>();

  public void registerValidator(final ClusterConfigurationRequestValidator<?> validator) {
    validators.put(validator.requestType(), validator);
  }

  public void deregisterValidator(
      final Class<? extends ClusterConfigurationManagementRequest> validatorType) {
    validators.remove(validatorType);
  }

  @SuppressWarnings("unchecked")
  public <T extends ClusterConfigurationManagementRequest> ActorFuture<Void> validateRequest(
      final T request) {

    final ClusterConfigurationRequestValidator<?> validator = validators.get(request.getClass());
    if (validator != null) {
      return validator.validate(request);
    }
    return CompletableActorFuture.completed();
  }

  @FunctionalInterface
  public interface RequestValidatorSupplier {
    ActorFuture<Void> validate(final ClusterConfigurationManagementRequest request);
  }
}
