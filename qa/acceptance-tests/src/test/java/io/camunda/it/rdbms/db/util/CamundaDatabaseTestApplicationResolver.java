/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.camunda.zeebe.util.ReflectUtil;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;

public class CamundaDatabaseTestApplicationResolver
    implements ParameterResolver, BeforeEachCallback {

  private final String standaloneCamundaKey;
  private final CamundaRdbmsTestApplication testApplication;

  public CamundaDatabaseTestApplicationResolver(
      final String standaloneCamundaKey, final CamundaRdbmsTestApplication testApplication) {
    this.standaloneCamundaKey = standaloneCamundaKey;
    this.testApplication = testApplication;
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
    return parameterCtx.getParameter().getType().equals(CamundaRdbmsTestApplication.class);
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
    final ExtensionContext rootContext = extensionCtx.getRoot();
    final ExtensionContext.Store store = rootContext.getStore(Namespace.GLOBAL);
    final String key = CamundaRdbmsTestApplication.class.getName() + "_" + standaloneCamundaKey;
    // This stores the app in the root context, so that have to start it just once
    // See: https://junit.org/junit5/docs/snapshot/user-guide/index.html#extensions-keeping-state
    return store.getOrComputeIfAbsent(
        key, __ -> testApplication, CamundaRdbmsTestApplication.class);
  }

  @Override
  public void beforeEach(final ExtensionContext context) {
    context.getRequiredTestInstances().getAllInstances().forEach(this::injectFields);
  }

  private void injectFields(final Object instance) {
    ReflectionUtils.findFields(
            instance.getClass(),
            field ->
                ReflectionUtils.isNotStatic(field)
                    && field.getType() == CamundaRdbmsTestApplication.class,
            HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                ReflectUtil.makeAccessible(field, instance).set(instance, testApplication);
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }
}
