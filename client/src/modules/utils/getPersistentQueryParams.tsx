/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const persistentQueryParams = ['gseUrl'];

function getPersistentQueryParams(search: string) {
  const params = Array.from(new URLSearchParams(search));
  return new URLSearchParams(
    params.filter(([key]) => {
      return persistentQueryParams.includes(key);
    })
  ).toString();
}
export {getPersistentQueryParams};
