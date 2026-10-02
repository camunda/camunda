/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Keep in sync with `io.camunda.security.configuration.SecurityConfiguration.DEFAULT_ID_REGEX`.
const MAPPING_RULE_ID_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;

function isValidMappingRuleId(mappingRuleId: string): boolean {
	return MAPPING_RULE_ID_PATTERN.test(mappingRuleId);
}

export {isValidMappingRuleId};
