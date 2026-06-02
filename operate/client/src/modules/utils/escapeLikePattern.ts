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
 *
 * Note: the v2 API's `$like` is case-sensitive. Case-insensitive search
 * (`$ilike`) is planned as a backend follow-up (see the backend filter ADR
 * tracking issue).
 */
const escapeLikePattern = (input: string): string => {
  const escaped = input.replace(/[\\*?]/g, '\\$&');
  return `*${escaped}*`;
};

export {escapeLikePattern};
