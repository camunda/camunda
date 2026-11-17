/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const VARIABLE_NAME_DOT_ESCAPE_CHAR = '___DOT___';

function createVariableFieldName(name: string): string {
	return `#${name.replaceAll('.', VARIABLE_NAME_DOT_ESCAPE_CHAR)}`;
}

function createNewVariableFieldName(prefix: string, suffix: string): string {
	return `${prefix}.${suffix}`;
}

function getVariableFieldName(variableNameWithPrefix: string): string {
	return variableNameWithPrefix.substring(1).replaceAll(VARIABLE_NAME_DOT_ESCAPE_CHAR, '.');
}

function getNewVariablePrefix(variableName: string): string {
	return variableName.replace('.name', '').replace('.value', '');
}

export {createNewVariableFieldName, createVariableFieldName, getNewVariablePrefix, getVariableFieldName};
