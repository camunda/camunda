/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValidId} from '#/admin/modules/shared/identifierPattern';

const AUTHORIZATION_WILDCARD = '*';
const SECRET_REFERENCE_PREFIX = 'camunda.secrets.';

// Keep in sync with
// `io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator.SECRET_NAME_PATTERN`.
const SECRET_NAME_PATTERN = /^[A-Za-z0-9_-]{1,240}$/;
const SECRET_RESOURCE_ID_PATTERN = /^(\*|camunda\.secrets\.[A-Za-z0-9_-]{1,240})$/;

// Keep in sync with
// `io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator.MAX_PROBE_ID_LENGTH`.
// Both count UTF-16 code units.
const MAX_PROBE_ID_LENGTH = 256;

// The server rejects a probe ID with a character Java's `Character.isWhitespace` accepts: a Unicode
// space separator other than a no-break space, a tab or line break (U+0009 to U+000D), or an
// information separator (U+001C to U+001F). JavaScript's `\s` differs, so it is not used here.
const UNICODE_SPACE_SEPARATOR = /(?![\u00A0\u2007\u202F])[\p{Zs}\p{Zl}\p{Zp}]/u;

function hasJavaWhitespace(id: string): boolean {
	for (let index = 0; index < id.length; index++) {
		const code = id.charCodeAt(index);
		if ((code >= 0x09 && code <= 0x0d) || (code >= 0x1c && code <= 0x1f)) {
			return true;
		}
	}
	return UNICODE_SPACE_SEPARATOR.test(id);
}

function isValidResourceId(id: string, configuredPattern?: string | null): boolean {
	return id === AUTHORIZATION_WILDCARD || isValidId(id, configuredPattern);
}

function isValidSecretResourceId(id: string): boolean {
	return SECRET_RESOURCE_ID_PATTERN.test(id);
}

// A probe ID is opaque, for example `validate-configuration/io.camunda:aws-credential:1`, so the
// configured ID pattern does not apply to it. The wildcard passes this check as it is.
function isValidProbeResourceId(id: string): boolean {
	return id.length > 0 && id.length <= MAX_PROBE_ID_LENGTH && !hasJavaWhitespace(id);
}

export {
	AUTHORIZATION_WILDCARD,
	SECRET_NAME_PATTERN,
	SECRET_REFERENCE_PREFIX,
	isValidProbeResourceId,
	isValidResourceId,
	isValidSecretResourceId,
};
