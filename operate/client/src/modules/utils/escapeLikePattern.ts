/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Escapes a user-provided substring so it can be safely passed to the v2 API's
 * `$like` operator without the user's `*` / `?` / `\` characters being
 * interpreted as wildcards.
 *
 * Returns a wildcard pattern that matches the input as a substring,
 * e.g. `escapeLikePattern("foo*bar") === "*foo\\*bar*"`.
 */
const escapeLikePattern = (input: string): string => {
  const escaped = input.replace(/[\\*?]/g, '\\$&');
  return `*${escaped}*`;
};

/**
 * Emits substring `$like` patterns that approximate a case-insensitive
 * substring search. The v2 API's `$like` operator is case-sensitive and there
 * is no `$ilike`, so the caller is expected to combine the returned patterns
 * with `$or` to cover the common case styles used in BPMN naming
 * (Title Case names, lower/UPPER case ids).
 *
 * For `"order"` this returns `["*order*", "*Order*", "*ORDER*"]`.
 * Duplicates are removed, so input that is already in one of the canonical
 * forms produces fewer entries.
 */
const escapeLikePatternsForCaseInsensitive = (input: string): string[] => {
  const variants = new Set<string>([
    input,
    input.toLowerCase(),
    input.toUpperCase(),
    capitalizeFirstLetter(input),
  ]);
  return Array.from(variants).map(escapeLikePattern);
};

const capitalizeFirstLetter = (s: string): string => {
  if (s.length === 0) {
    return s;
  }
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
};

export {escapeLikePattern, escapeLikePatternsForCaseInsensitive};
