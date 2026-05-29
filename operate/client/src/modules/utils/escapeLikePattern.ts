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
 * Capitalises the first letter of every whitespace-separated word and
 * lowercases the remainder, e.g. `"order TASK"` → `"Order Task"`.
 */
const toTitleCase = (s: string): string =>
  s.replace(
    /\S+/g,
    (w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase(),
  );

/**
 * Emits substring `$like` patterns that approximate a case-insensitive
 * substring search. The v2 API's `$like` operator is case-sensitive and there
 * is no `$ilike`, so the caller is expected to combine the returned patterns
 * with `$or` to cover the naming conventions used in BPMN
 * (Title Case names, lower-case, UPPER-CASE IDs).
 *
 * For `"order task"` this returns `["*order task*", "*ORDER TASK*", "*Order Task*"]`.
 * Duplicates are removed, so input that is already in one of the canonical
 * forms produces fewer entries.
 *
 * Note: arbitrary mixed-case input (e.g. `"vAliDate"`) will not be matched by
 * typing `"validate"` — this is a best-effort approximation, not true `$ilike`.
 */
const escapeLikePatternsForCaseInsensitive = (input: string): string[] => {
  const variants = new Set<string>([
    input,
    input.toLowerCase(),
    input.toUpperCase(),
    toTitleCase(input),
  ]);
  return Array.from(variants).map(escapeLikePattern);
};

export {escapeLikePattern, escapeLikePatternsForCaseInsensitive};
