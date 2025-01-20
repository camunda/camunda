/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function getFullURL(url: string) {
  if (typeof window.location.origin !== 'string') {
    throw new Error('window.location.origin is not a set');
  }

  return new URL(url, window.location.origin).toString();
}
