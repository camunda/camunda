/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function flattenObject(
  data: unknown,
  basePath: string = '',
): Map<string, unknown> {
  const result = new Map<string, unknown>();

  if (data === null || data === undefined) {
    return result;
  }

  if (typeof data !== 'object') {
    return result;
  }

  const entries = Array.isArray(data)
    ? data.map((val, index) => [index.toString(), val])
    : Object.entries(data);

  for (const [key, value] of entries) {
    let currentPath = key;

    if (basePath.length > 0) {
      currentPath = Array.isArray(data)
        ? `${basePath}[${key}]`
        : `${basePath}.${key}`;
    }

    if (value === null || value === undefined || typeof value !== 'object') {
      result.set(currentPath, value);
    } else {
      const nestedPaths = flattenObject(value, currentPath);

      for (const [nestedPath, nestedValue] of nestedPaths) {
        result.set(nestedPath, nestedValue);
      }
    }
  }

  return result;
}

function extractFilePath(data: unknown): Map<string, string> {
  const flattened = flattenObject(data);
  const filePaths = new Map<string, string>();

  for (const [path, value] of flattened.entries()) {
    if (typeof value === 'string' && value.startsWith('files::')) {
      filePaths.set(value, path);
    }
  }

  return filePaths;
}

export {extractFilePath};
