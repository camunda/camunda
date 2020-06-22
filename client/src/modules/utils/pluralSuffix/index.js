/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * Adds an 's' at the end of a text, when count is not 1 or -1
 */
export default function pluralSuffix(count, text) {
  return Math.abs(count) === 1 ? `${count} ${text}` : `${count} ${text}s`;
}
