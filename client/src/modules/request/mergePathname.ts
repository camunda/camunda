/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function mergePathname(prefix: string, pathname: string) {
  const SLASH_ON_END_PATTERN = /\/$/i;
  const SLASH_ON_BEGINNING_PATTERN = /^\//i;

  if (
    SLASH_ON_END_PATTERN.test(prefix) &&
    SLASH_ON_BEGINNING_PATTERN.test(pathname)
  ) {
    return `${prefix.replace(SLASH_ON_END_PATTERN, '')}${pathname}`;
  }

  if (
    (!SLASH_ON_END_PATTERN.test(prefix) &&
      SLASH_ON_BEGINNING_PATTERN.test(pathname)) ||
    (SLASH_ON_END_PATTERN.test(prefix) &&
      !SLASH_ON_BEGINNING_PATTERN.test(pathname))
  ) {
    return `${prefix}${pathname}`;
  }

  return `${prefix}/${pathname}`;
}

export {mergePathname};
