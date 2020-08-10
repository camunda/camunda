/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';
import {onError} from '@apollo/client/link/error';

import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';
import {login} from 'modules/stores/login';

const client = new ApolloClient({
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          tasks: {
            merge(existing, incoming) {
              return {...existing, ...incoming}; // TODO - Issue #243
            },
          },
        },
      },
    },
  }),
  link: onError((error) => {
    const {networkError} = error;

    // @ts-ignore - TODO[Vinicius]: check why type defs are wrong here - Issue #68
    if ([401, 403].includes(networkError?.statusCode)) {
      resetApolloStore().then(login.disableSession);
    } else {
      console.error(error);
    }
  }).concat(
    new HttpLink({
      uri: '/graphql',
      fetch(uri: RequestInfo, options: RequestInit) {
        const token = getCsrfToken(document.cookie);

        if (token !== null) {
          options.headers = {
            ...options.headers,
            [CsrfKeyName]: token,
          };
        }

        return fetch(uri, options);
      },
    }),
  ),
});

async function resetApolloStore() {
  await client.clearStore();
  client.stop();
}

export {client, resetApolloStore};
