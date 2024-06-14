/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fs from 'fs';
import fetch from 'node-fetch';
import JSON5 from 'json5';

export async function fetchUrl<T>(url: string, authHeader: string): Promise<T> {
  try {
    const response = await fetch(url, {
      headers: {Authorization: authHeader},
    });

    return response.json() as Promise<T>;
  } catch (error) {
    console.error(error);
    process.exit(1);
  }
}

export function createJsonFile(fileName: string, json: Record<string, unknown>) {
  return fs.writeFileSync(fileName, JSON.stringify(json), 'utf-8');
}

export function readJsonFIle<T extends Record<string, unknown>>(fileName: string) {
  return JSON5.parse(fs.readFileSync(fileName, 'utf-8')) as T;
}

export function isRegExp(value: string): boolean {
  return value.startsWith('/') && value.endsWith('/');
}

export function matchRegex(string: string, regex: RegExp): string | undefined {
  const match = string.match(regex);
  return match?.[1];
}
