/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;

public class IdentityContainer {

  Identity identity;
  IdentityConfiguration identityConfiguration;

  public IdentityContainer(
      final Identity identity, final IdentityConfiguration identityConfiguration) {
    this.identity = identity;
    this.identityConfiguration = identityConfiguration;
  }

  public Identity getIdentity() {
    return identity;
  }
}
