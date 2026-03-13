/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import io.camunda.gatekeeper.model.identity.CamundaUserInfo;

/**
 * SPI for providing the current authenticated user's information. Implementations resolve user
 * profile details, authorization, and membership based on the current authentication context.
 */
public interface CamundaUserProvider {

  /** Returns the current user's info, or null if not authenticated. */
  CamundaUserInfo getCurrentUser();

  /** Returns the user's access token, or null if not applicable (e.g. basic auth). */
  String getUserToken();
}
