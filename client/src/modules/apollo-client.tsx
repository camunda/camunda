/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';

import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';
import {login} from 'modules/stores/login';
import {mergePathname} from 'modules/utils/mergePathname';
import {MAX_TASKS_DISPLAYED} from 'modules/constants/tasks';

const client = new ApolloClient({
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          tasks: {
            keyArgs: false,
            merge(existing, incoming, {args}) {
              let merged = existing ? existing.slice(0) : [];

              let result;

              // requesting next page
              if (args?.query?.searchAfter !== undefined) {
                merged.push(...incoming);
                result = merged.slice(
                  Math.max(merged.length - MAX_TASKS_DISPLAYED, 0),
                );
              }
              // requesting previous page
              else if (args?.query?.searchBefore !== undefined) {
                if (incoming.length > 0) {
                  merged.unshift(...incoming);
                }

                result = merged.slice(0, MAX_TASKS_DISPLAYED);
              }
              // initial request / polling / refreshing after mutations
              else {
                result = incoming;
              }

              return result;
            },
          },
        },
      },
    },
  }),
  link: new HttpLink({
    uri: mergePathname(window.clientConfig?.contextPath ?? '/', '/graphql'),
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
