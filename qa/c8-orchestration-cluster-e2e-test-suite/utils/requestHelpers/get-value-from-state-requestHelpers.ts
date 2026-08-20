/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function groupIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`groupId${state[key]}${nth}`] as string;
}

export function roleIdValueUsingKey(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleId${state[key]}${nth}`] as string;
}

export function clientIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`clientId${state[key]}${nth}`] as string;
}

export function mappingRuleIdFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`mappingRuleId${state[key]}${nth}`] as string;
}

export function mappingRuleIdFromKey(
  key: string,
  state: Record<string, unknown>,
): string {
  return state[`mappingRuleId${key}`] as string;
}

export function mappingRuleNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`name${state[key]}${nth}`] as string;
}

export function mappingRuleClaimNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`claimName${state[key]}${nth}`] as string;
}

export function mappingRuleClaimValueFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`claimValue${state[key]}${nth}`] as string;
}

export function userFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`username${state[key]}${nth}`] as string;
}

export function clientFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`client${state[key]}${nth}`] as string;
}

export function roleNameFromState(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleName${state[key]}${nth}`] as string;
}

export function roleDescriptionFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`roleDescription${state[group]}${nth}`] as string;
}
