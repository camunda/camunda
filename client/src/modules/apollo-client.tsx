/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';

import {authenticationStore} from 'modules/stores/authentication';
import {mergePathname} from 'modules/utils/mergePathname';
import {MAX_TASKS_DISPLAYED} from 'modules/constants/tasks';

type CreateApolloClientOptions = {maxTasksDisplayed: number};
const defaultCreateApolloClient = {
  maxTasksDisplayed: MAX_TASKS_DISPLAYED,
} as const;
function createApolloClient({
  maxTasksDisplayed,
}: CreateApolloClientOptions = defaultCreateApolloClient) {
  return new ApolloClient({
    cache: new InMemoryCache({
      typePolicies: {
        Query: {
          fields: {
            currentUser: {
              read(user) {
                if (user === undefined || user.permissions === undefined) {
                  return user;
                }

                return {
                  ...user,
                  permissions: user?.permissions.map((permission: string) =>
                    permission.toLowerCase(),
                  ),
                };
              },
            },
            tasks: {
              keyArgs: false,
              merge(existing, incoming, {args}) {
                let merged = existing ? existing.slice(0) : [];

                let result;

                // requesting next page
                if (args?.query?.searchAfter !== undefined) {
                  merged.push(...incoming);
                  result = merged.slice(
                    Math.max(merged.length - maxTasksDisplayed, 0),
                  );
                }
                // requesting previous page
                else if (args?.query?.searchBefore !== undefined) {
                  if (incoming.length > 0) {
                    merged.unshift(...incoming);
                  }

                  result = merged.slice(0, maxTasksDisplayed);
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
        const response = await fetch(uri, options);
        if (response.ok) {
          authenticationStore.activateSession();
        }

        if ([401, 403].includes(response.status)) {
          await resetApolloStore();
          authenticationStore.disableSession();
        }

        return response;
      },
    }),
  });
}

const client = createApolloClient();

async function resetApolloStore() {
  await client.clearStore();
  client.stop();
}

async function clearClientCache() {
  await client.cache.reset();
}

export {client, resetApolloStore, clearClientCache, createApolloClient};
