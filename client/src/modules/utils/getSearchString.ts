/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function getSearchString() {
  const HASH_PATHNAME_PATTERN = /^#(\/\w{1,}\/*)+\?/;
  const hash = window.location.hash;

  if (HASH_PATHNAME_PATTERN.test(hash)) {
    return hash.replace(HASH_PATHNAME_PATTERN, '');
  }

  return '';
}

export {getSearchString};
