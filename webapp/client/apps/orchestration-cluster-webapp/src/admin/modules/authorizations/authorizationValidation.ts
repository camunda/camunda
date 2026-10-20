/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const AUTHORIZATION_WILDCARD = '*';
const SECRET_REFERENCE_PREFIX = 'camunda.secrets.';

// Keep in sync with `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`.
const DEFAULT_ID_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;

// Keep in sync with
// `io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator.SECRET_NAME_PATTERN`.
const SECRET_NAME_PATTERN = /^[A-Za-z0-9_-]{1,240}$/;
const SECRET_RESOURCE_ID_PATTERN = /^(\*|camunda\.secrets\.[A-Za-z0-9_-]{1,240})$/;

function getIdPattern(configuredPattern: string | null | undefined): RegExp {
	return configuredPattern ? new RegExp(configuredPattern) : DEFAULT_ID_PATTERN;
}

function isValidId(id: string, configuredPattern?: string | null): boolean {
	return getIdPattern(configuredPattern).test(id);
}

function isValidResourceId(id: string, configuredPattern?: string | null): boolean {
	return id === AUTHORIZATION_WILDCARD || isValidId(id, configuredPattern);
}

function isValidSecretResourceId(id: string): boolean {
	return SECRET_RESOURCE_ID_PATTERN.test(id);
}

export {
	AUTHORIZATION_WILDCARD,
	SECRET_NAME_PATTERN,
	SECRET_REFERENCE_PREFIX,
	getIdPattern,
	isValidId,
	isValidResourceId,
	isValidSecretResourceId,
};
