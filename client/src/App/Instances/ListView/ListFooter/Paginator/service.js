/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function getRange(focus, max) {
  let start = Math.max(focus - 2, 1);
  let end = Math.min(start + 4, max);
  if (max - focus < 2) {
    start = Math.max(end - 4, 1);
  }

  const pages = [];
  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  return pages;
}

export function getCurrentPage(firstElement, perPage) {
  return Math.round(firstElement / perPage) + 1;
}
