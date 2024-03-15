/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import io.camunda.common.exception.SdkException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for Authentication Typically you will replace this by a proper
 * authentication by setting the right properties
 */
public class DefaultNoopAuthentication implements Authentication {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String errorMessage =
      "Unable to determine authentication. Please check your configuration";

  public DefaultNoopAuthentication() {
    LOG.error(errorMessage);
  }

  @Override
  public void resetToken(final Product product) {
    throw new SdkException(errorMessage);
  }

  @Override
  public Map.Entry<String, String> getTokenHeader(final Product product) {
    throw new UnsupportedOperationException("Unable to determine authentication");
  }
}
