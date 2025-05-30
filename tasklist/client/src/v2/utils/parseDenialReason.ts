/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const parseDenialReason = async (
  error: unknown,
  type: 'assignment' | 'unassignment' | 'completion',
): Promise<string | undefined> => {
  const response = (error as {response?: Response})?.response;
  if (!response || response.status !== 409) return undefined;

  const responseJson = await response?.json();

  const detail =
    typeof responseJson?.detail === 'string' ? responseJson?.detail : '';

  let deniedReason: string = `The task ${type} was rejected by the system.`;
  const match = detail.match(/Reason to deny:\s*(.*)/i);
  if (match && match[1]) {
    deniedReason = match[1].trim().replace(/^["'](.*)["']$/, '$1');
  }

  return deniedReason;
};
