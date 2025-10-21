/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function createFilter(
  filterKey: string,
  filterValue: string,
  state: Record<string, unknown>,
): {key: string; value: unknown} {
  if (filterValue === '') {
    if (filterKey === 'processDefinitionKey')
      // Use value from state
      return {key: filterKey, value: state.processDefinitionKey};
    else if (filterKey === 'processInstanceKey')
      return {key: filterKey, value: state.processInstanceKey};
    else throw new Error('Unsupported filter key for empty value');
  } else return {key: filterKey, value: filterValue};
}
