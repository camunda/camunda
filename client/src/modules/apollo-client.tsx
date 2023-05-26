/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';
import {CurrentUser} from 'modules/types';
import {authenticationStore} from 'modules/stores/authentication';
import {mergePathname} from 'modules/utils/mergePathname';
import uniqBy from 'lodash/uniqBy';

function createApolloClient() {
  return new ApolloClient({
    cache: new InMemoryCache({
      typePolicies: {
        Query: {
          fields: {
            currentUser: {
              read(user: CurrentUser | undefined) {
                if (user?.permissions === undefined) {
                  return user;
                }

                return {
                  ...user,
                  permissions: user.permissions.map((permission: string) =>
                    permission.toLowerCase(),
                  ),
                };
              },
            },
            tasks: {
              merge(_, incoming) {
                return uniqBy(incoming, '__ref');
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

export {client, resetApolloStore, clearClientCache};
