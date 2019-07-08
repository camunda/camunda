/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function getToken(cookies) {
  let token;

  cookies
    .replace(/ /g, '')
    .split(';')
    .forEach(cookie => {
      const items = cookie.split('=');

      if (items[0] === 'X-CSRF-TOKEN') {
        token = items[1];
      }
    });

  return token;
}
