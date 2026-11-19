/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

/**
 * The state a secret store reports for a listed secret. A store that cannot tell reports every
 * secret as {@link #ACTIVE}.
 */
public enum SecretStatus {
  /** The secret can be used. */
  ACTIVE,
  /** The secret was switched off in the store. */
  DISABLED,
  /** The secret's validity period has ended. */
  EXPIRED,
  /** The secret's validity period has not begun yet. */
  NOT_YET_ACTIVE
}
