/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function flatten(ctx = '', suffix = () => '') {
  return (flat, entry) => {
    if (entry.columns) {
      // nested column, flatten recursivly with augmented context
      return flat.concat(
        entry.columns.reduce(flatten(ctx + (entry.id || entry.label), suffix), [])
      );
    } else {
      // normal column, return current context with optional suffix
      return flat.concat(ctx + suffix(entry));
    }
  };
}
