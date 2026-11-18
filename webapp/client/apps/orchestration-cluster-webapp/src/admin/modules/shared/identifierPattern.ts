/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Keep in sync with `io.camunda.security.spring.CamundaSecurityLibraryProperties.DEFAULT_ID_REGEX`
// (from the `camunda-security-library-validation`/`-spring-boot-starter` artifacts), which
// `IdentifierValidator` applies to every admin-managed identifier server-side — mapping rule IDs,
// global task listener IDs and types, role/group/tenant IDs, etc. The Admin client config serves
// it as `idPattern` unless a deployment replaces it with `camunda.security.id-validation-pattern`.
const DEFAULT_ID_PATTERN = '^[a-zA-Z0-9_~@.+-]+$';
// The server caps IDs separately from the pattern, so a configured pattern need not bound the length.
const ID_MAX_LENGTH = 256;

function isCustomIdPattern(configuredPattern?: string | null): configuredPattern is string {
	return Boolean(configuredPattern) && configuredPattern !== DEFAULT_ID_PATTERN;
}

function getIdPatternText(configuredPattern?: string | null): string {
	return configuredPattern || DEFAULT_ID_PATTERN;
}

// Unicode mode makes classes such as `\p{L}` mean what they mean in Java. A pattern the browser
// still cannot parse, such as one using Java-only syntax, is left to the server.
function compileIdPattern(source: string): RegExp | null {
	try {
		return new RegExp(source, 'u');
	} catch {
		return null;
	}
}

function isValidId(value: string, configuredPattern?: string | null): boolean {
	const pattern = compileIdPattern(getIdPatternText(configuredPattern));
	return value.length <= ID_MAX_LENGTH && (pattern === null || pattern.test(value));
}

export {getIdPatternText, isCustomIdPattern, isValidId};
