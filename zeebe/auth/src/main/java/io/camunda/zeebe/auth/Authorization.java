/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

public class Authorization {

  public static final String AUTHORIZED_ANONYMOUS_USER = "authorized_anonymous_user";
  public static final String AUTHORIZED_TENANTS = "authorized_tenants";
  public static final String AUTHORIZED_USERNAME = "authorized_username";
  public static final String USER_TOKEN_CLAIM_PREFIX = "user_token_";
}
