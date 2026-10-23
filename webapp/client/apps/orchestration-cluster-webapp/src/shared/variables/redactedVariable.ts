/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Variable values are JSON-encoded, so the redacted value is a JSON string literal and stays parseable.
const REDACTED_VALUE = JSON.stringify('[REDACTED]');

type ProtectableVariable = {metadata?: {protectionModes: readonly string[]} | null};

function isRedactedVariable(variable: ProtectableVariable): boolean {
	return variable.metadata?.protectionModes.includes('REDACT') ?? false;
}

function redactValue<T extends ProtectableVariable & {value: string}>(variable: T): T {
	return isRedactedVariable(variable) ? {...variable, value: REDACTED_VALUE} : variable;
}

function redactValues<T extends ProtectableVariable & {value: string}, R extends {items: T[]}>(result: R): R {
	return {...result, items: result.items.map(redactValue)};
}

export {isRedactedVariable, redactValue, redactValues};
