/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ClassValue = string | undefined | null | false | Record<string, unknown>;

function cn(...args: ClassValue[]): string {
  const classes: string[] = [];
  for (const arg of args) {
    if (!arg) {
      continue;
    }
    if (typeof arg === 'string') {
      classes.push(arg);
    } else {
      for (const [key, value] of Object.entries(arg)) {
        if (value) {
          classes.push(key);
        }
      }
    }
  }
  return classes.join(' ');
}

export {cn};
