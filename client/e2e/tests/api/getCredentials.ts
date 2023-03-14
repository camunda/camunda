/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';
import memoize from 'lodash/memoize';
import {ENDPOINTS} from './endpoints';
import {config} from '../../config';

const sessionToken = /^OPERATE-SESSION=[0-9A-Z]{32}$/i;

type TokensType = {
  session?: string;
};

const getCredentials = memoize(
  async (): Promise<{
    Cookie: string;
  }> => {
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

        return accumulator;
      }, {});

    if (!tokens.hasOwnProperty('session')) {
      throw new Error('Missing credential');
    }

    return {
      Cookie: `${tokens.session};`,
    };
  }
);

export {getCredentials};
