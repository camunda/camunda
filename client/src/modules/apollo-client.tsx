/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';

import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';
import {login} from 'modules/stores/login';

const client = new ApolloClient({
  cache: new InMemoryCache(), //TODO - Issue #243
  link: new HttpLink({
    uri: '/graphql',
    async fetch(uri: RequestInfo, options: RequestInit) {
      const token = getCsrfToken(document.cookie);

      if (token !== null) {
        options.headers = {
          ...options.headers,
          [CsrfKeyName]: token,
        };
      }

      const response = await fetch(uri, options);
      if (response.ok) {
        login.activateSession();
      }

      if ([401, 403].includes(response.status)) {
        await resetApolloStore();
        login.disableSession();
      }

      return response;
    },
  }),
});

async function resetApolloStore() {
  await client.clearStore();
  client.stop();
}

export {client, resetApolloStore};
