/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * if the parsed value is an object, it returns it as a string
 */
export function safelyParseValue(value) {
  const parsed = JSON.parse(value);
  return typeof parsed === 'object' ? value : parsed;
}
