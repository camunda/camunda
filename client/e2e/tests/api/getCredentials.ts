/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import fetch from 'node-fetch';
import {memoize} from 'lodash';
import {ENDPOINTS} from './endpoints';
import {config} from '../../config';

const sessionToken = /^JSESSIONID=[0-9A-Z]{32}$/i;
const csrfToken = /^X-CSRF-TOKEN=[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type TokensType = {
  csrf?: string;
  session?: string;
};

const getCredentials = memoize(
  async (): Promise<
    | {
        Cookie: string;
      }
    | {
        Cookie: string;
        'X-CSRF-TOKEN': string;
      }
  > => {
    const {username, password} = config.agentUser;
    const {headers} = await fetch(ENDPOINTS.login(), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams([
        ['username', username],
        ['password', password],
      ]).toString(),
    });
    const setCookie = headers.get('set-cookie');

    if (setCookie === null) {
      throw new Error('Credentials not present in the response');
    }

    const tokens: TokensType = setCookie
      .split(', ')
      .reduce((accumulator, cookie) => {
        const [cookieValue] = cookie.split(';');

        if (sessionToken.test(cookieValue)) {
          return {...accumulator, session: cookieValue};
        }

        if (csrfToken.test(cookieValue)) {
          return {...accumulator, csrf: cookieValue};
        }

        return accumulator;
      }, {});

    if (!tokens.hasOwnProperty('session')) {
      throw new Error('Missing credential');
    }

    if (!tokens.hasOwnProperty('csrf')) {
      return {
        Cookie: `${tokens.session}`,
      };
    }

    return {
      'X-CSRF-TOKEN':
        tokens.csrf === undefined ? '' : tokens.csrf.split('=')[1],
      Cookie: `${tokens.session}; ${tokens.csrf}`,
    };
  }
);

export {getCredentials};
