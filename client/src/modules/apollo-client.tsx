/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import ApolloClient from 'apollo-boost';

import {resolvers} from 'modules/mock-schema/resolvers';
import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';
import {login} from 'modules/stores/login';

const client = new ApolloClient({
  uri: '/graphql',
  resolvers,
  request(operation) {
    const token = getCsrfToken(document.cookie);

    if (token !== null) {
      operation.setContext({
        headers: {
          [CsrfKeyName]: token,
        },
      });
    }
  },
  onError(error) {
    const {networkError} = error;

    // @ts-ignore - TODO[Vinicius]: check why type defs are wrong here - Issue #68
    if ([401, 403].includes(networkError?.statusCode)) {
      resetApolloStore();
      login.disableSession();
    }

    console.error(error);
  },
});

const resetApolloStore = () => {
  client.clearStore();
  client.stop();
};

export {client, resetApolloStore};
