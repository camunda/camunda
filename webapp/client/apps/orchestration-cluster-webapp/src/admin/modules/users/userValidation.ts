/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Keep in sync with `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`.
const USERNAME_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;

const EMAIL_PATTERN =
	/^(([^<>()[\].,;:\s@"]+(\.[^<>()[\].,;:\s@"]+)*)|(".+"))@(([^<>()[\].,;:\s@"]+\.)+[^<>()[\].,;:\s@"]{2,})$/i;

function isValidUsername(username: string): boolean {
	return USERNAME_PATTERN.test(username);
}

function isValidEmail(email: string): boolean {
	return EMAIL_PATTERN.test(email);
}

export {isValidUsername, isValidEmail};
