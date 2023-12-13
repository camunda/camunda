/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import mergeWith from 'lodash/mergeWith';

type UnknownObject = Record<string, unknown>;

function mergeVariables(
  initialVariables: UnknownObject,
  newVariables: UnknownObject,
): UnknownObject {
  const modifiedVariables = Object.fromEntries(
    Object.keys(newVariables)
      .map((key) => [key, initialVariables[key]])
      .filter(([, value]) => value !== undefined),
  );
  return mergeWith(modifiedVariables, newVariables, (_, newValue) =>
    Array.isArray(newValue) ? newValue : undefined,
  );
}

export {mergeVariables};
