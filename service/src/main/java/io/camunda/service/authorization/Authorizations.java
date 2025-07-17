/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.authorization;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.security.auth.Authorization;

public abstract class Authorizations {

  public static final Authorization<AuthorizationEntity> AUTHORIZATION_READ_AUTHORIZATION =
      Authorization.of(a -> a.authorization().read());

  public static final Authorization<ProcessInstanceEntity> PROCESS_INSTANCE_READ_AUTHORIZATION =
      Authorization.of(a -> a.processDefinition().readProcessInstance());
}
