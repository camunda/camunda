/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const createVariableFieldName = (name: string): `#${string}` => `#${encodeURIComponent(name).replace(/\./g, '%2E')}`;

const createNewVariableFieldName = (prefix: string, suffix: 'id' | 'name' | 'value') => `${prefix}.${suffix}`;

const getNewVariablePrefix = (fieldName: string) => fieldName.replace('.name', '').replace('.value', '');

export {createVariableFieldName, createNewVariableFieldName, getNewVariablePrefix};
