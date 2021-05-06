/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function getToken(cookies: string) {
  return cookies
    .replace(/ /g, '')
    .split(';')
    .reduce<undefined | string>((accumulator, cookie) => {
      const [name, value] = cookie.split('=');

      if (name === 'OPERATE-X-CSRF-TOKEN') {
        return value;
      }

      return accumulator;
    }, undefined);
}
