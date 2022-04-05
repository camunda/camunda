/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
