/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';

const REDACTED_VALUE_LABEL = '[REDACTED]';

function isRedactedVariable(variable: Pick<Variable, 'value' | 'protectionModes'>): boolean {
	return (variable.protectionModes ?? []).includes('REDACT') && (variable.value === null || variable.value === 'null');
}

export {isRedactedVariable, REDACTED_VALUE_LABEL};
