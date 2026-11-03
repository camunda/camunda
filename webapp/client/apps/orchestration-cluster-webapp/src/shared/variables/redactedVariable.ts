/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const JSON_ENCODED_REDACTED_VALUE = JSON.stringify('[REDACTED]');

type ProtectableVariable = {metadata?: {protectionModes: readonly string[]} | null};
type RedactableVariable = ProtectableVariable & {value: string; isTruncated: boolean};

function isRedactedVariable(variable: ProtectableVariable): boolean {
	return variable.metadata?.protectionModes.includes('REDACT') ?? false;
}

function redactValue<T extends RedactableVariable>(variable: T): T {
	return isRedactedVariable(variable)
		? {...variable, value: JSON_ENCODED_REDACTED_VALUE, isTruncated: false}
		: variable;
}

function redactValues<T extends RedactableVariable, R extends {items: T[]}>(result: R): R {
	return {...result, items: result.items.map(redactValue)};
}

export {isRedactedVariable, redactValue, redactValues};
