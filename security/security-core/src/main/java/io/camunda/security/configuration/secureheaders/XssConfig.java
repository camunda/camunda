/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.XssHeaderModes;

public class XssConfig {

  private XssHeaderModes mode = XssHeaderModes.ENABLED;

  public XssHeaderModes getMode() {
    return mode;
  }

  public void setMode(final XssHeaderModes mode) {
    this.mode = mode;
  }
}
