/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

const CsrfKeyName = 'TASKLIST-X-CSRF-TOKEN';

function getCsrfToken(cookies: string) {
  return cookies
    .replace(/ /g, '')
    .split(';')
    .reduce<string | null>((token, cookie) => {
      const [cookieKey, value] = cookie.split('=');

      if (cookieKey === CsrfKeyName) {
        return value;
      }

      return token;
    }, null);
}

export {getCsrfToken, CsrfKeyName};
