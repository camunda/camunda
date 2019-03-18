/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * only parse non string and non object values
 */
export function safelyParseValue(value) {
  const parsed = JSON.parse(value);
  return ['object', 'string'].includes(typeof parsed) ? value : parsed;
}
