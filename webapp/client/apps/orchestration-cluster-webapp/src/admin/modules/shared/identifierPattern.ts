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
// global task listener IDs and types, role/group/tenant IDs, etc.
const ID_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;

function isValidId(value: string): boolean {
	return ID_PATTERN.test(value);
}

export {isValidId};
