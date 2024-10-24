/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CamundaDatabaseTestApplicationParameterResolver implements ParameterResolver {

  private final String standaloneCamundaKey;
  private final CamundaRdbmsTestApplication testApplication;

  public CamundaDatabaseTestApplicationParameterResolver(
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
}
