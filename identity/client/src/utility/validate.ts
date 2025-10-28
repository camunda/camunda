/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Validate an ID with the same rules as on the Backend side.
 * See: `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`.
 */
export const isValidId = (id: string): boolean =>
  /^[a-zA-Z0-9_~@.+-]{1,256}$/.test(id);

export const AUTHORIZATION_WILDCARD = "*";

export const isValidResourceId = (id: string): boolean =>
  isValidId(id) || id === AUTHORIZATION_WILDCARD;

/**
 * Because tenant IDs are used widely in the system and also part of many messages and events,
 * they are more heavily restricted than other IDs.
 */
export const isValidTenantId = (id: string): boolean =>
  /^[\w\.-]{1,31}$/.test(id);

export function isValidEmail(email: string): boolean {
  return /^(([^<>()[\].,;:\s@"]+(\.[^<>()[\].,;:\s@"]+)*)|(".+"))@(([^<>()[\].,;:\s@"]+\.)+[^<>()[\].,;:\s@"]{2,})$/i.test(
    email,
  );
}
