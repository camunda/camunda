/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Keep in sync with `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`
const DEFAULT_ID_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;
const ID_MAX_LENGTH = 256;

const getCompiledIdPattern = (): RegExp => {
  const configuredPattern = window.clientConfig?.idPattern;
  if (!configuredPattern) {
    return DEFAULT_ID_PATTERN;
  }
  return new RegExp(configuredPattern);
};

export const getIdPattern = (): string => {
  return getCompiledIdPattern().toString();
};

/**
 * Validate an ID with the same rules as on the Backend side.
 * See: `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`.
 */
export const isValidId = (id: string): boolean => {
  return id.length < ID_MAX_LENGTH && getCompiledIdPattern().test(id);
};

export const AUTHORIZATION_WILDCARD = "*";

export const isValidResourceId = (id: string): boolean =>
  isValidId(id) || id === AUTHORIZATION_WILDCARD;

/** The prefix every {@link isValidSecretResourceId}-matching reference starts with. */
export const SECRET_REFERENCE_PREFIX = "camunda.secrets.";

// Keep in sync with
// `io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator.SECRET_NAME_PATTERN`.
// 240 = SecretServices.MAX_REFERENCE_LENGTH (256) minus SECRET_REFERENCE_PREFIX's own length
// (16): a name any longer could never fit in a resolvable reference.
const SECRET_NAME_PATTERN = /^[A-Za-z0-9_-]{1,240}$/;

/**
 * Pattern for just the `<name>` portion a user types into the Resource ID field — the
 * `camunda.secrets.` prefix is rendered outside the input, so a message about the full
 * reference pattern would describe characters the field never lets them type.
 */
export const getSecretNamePattern = (): string =>
  SECRET_NAME_PATTERN.toString();

// Keep in sync with
// `io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator.SECRET_RESOURCE_ID_PATTERN`.
const SECRET_RESOURCE_ID_PATTERN =
  /^(\*|camunda\.secrets\.[A-Za-z0-9_-]{1,240})$/;

/**
 * A SECRET authorization's resource id must be the wildcard or a full
 * `camunda.secrets.<name>` reference — anything else can never match a secret
 * reference (camunda/camunda#62736).
 */
export const isValidSecretResourceId = (id: string): boolean =>
  SECRET_RESOURCE_ID_PATTERN.test(id);

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

export function isValidDate(date: string): boolean {
  return !isNaN(Date.parse(date));
}
