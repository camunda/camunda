/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;

public class Interceptor extends BaseExternalCodeConfiguration {

  public InterceptorCfg toInterceptorCfg() {
    final var interceptorCfg = new InterceptorCfg();
    interceptorCfg.setId(getId());
    interceptorCfg.setJarPath(getJarPath());
    interceptorCfg.setClassName(getClassName());
    return interceptorCfg;
  }

  @Override
  public Interceptor clone() {
    final Interceptor copy = new Interceptor();
    copy.setId(getId());
    copy.setJarPath(getJarPath());
    copy.setClassName(getClassName());

    return copy;
  }
}
