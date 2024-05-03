/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Adds an 's' at the end of a text, when count is not 1 or -1
 */
export default function pluralSuffix(count: number, text: string) {
  return Math.abs(count) === 1 ? `${count} ${text}` : `${count} ${text}s`;
}
