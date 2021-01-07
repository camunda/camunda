/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.test.util.testcontainers.ManagedVolume;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * This extension injects a new {@link ContainerState} at runtime into any test which adds a {@link
 * ContainerState} parameter, and stores it in a {@link Store}. This ensures that the resource is
 * properly closed (since {@link ContainerState} implements {@link CloseableResource}).
 *
 * <p>Note however that it currently only supports injecting a single state, since the stored state
 * will get overwritten.
 */
final class ContainerStateExtension implements AfterTestExecutionCallback, ParameterResolver {
  private static final Namespace NAMESPACE = Namespace.create(ContainerStateExtension.class);

  @Override
  public void afterTestExecution(final ExtensionContext context) {
    final Store store = context.getStore(NAMESPACE);
    final boolean hasFailed = context.getExecutionException().isPresent();
    if (hasFailed) {
      Optional.ofNullable(store.get(context.getUniqueId(), ContainerState.class))
          .ifPresent(ContainerState::onFailure);
    }
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == ContainerState.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    final Store store = extensionContext.getStore(NAMESPACE);
    final ContainerState state = new ContainerState().withVolume(ManagedVolume.newVolume());
    store.put(extensionContext.getUniqueId(), state);

    return state;
  }
}
