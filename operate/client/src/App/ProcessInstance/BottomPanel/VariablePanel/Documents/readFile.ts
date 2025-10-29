/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export async function readFile(path: string): Promise<string> {
  try {
    const response = await fetch(path);
    if (!response.ok) {
      throw new Error(`Failed to load file: ${response.statusText}`);
    }
    const text = await response.text();
    
    // Try to parse as JSON to validate and pretty-print
    try {
      const json = JSON.parse(text);
      return JSON.stringify(json, null, 2);
    } catch {
      // If not valid JSON, return as-is
      return text;
    }
  } catch (error) {
    console.error('Error reading file:', error);
    return '{}';
  }
}
