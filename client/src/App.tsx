/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';
import {BrowserRouter, Switch, Route} from 'react-router-dom';
import {ThemeProvider} from 'styled-components';
import ApolloClient from 'apollo-boost';
import {ApolloProvider} from '@apollo/react-hooks';

import {PrivateRoute} from './PrivateRoute';
import {Tasklist} from './Tasklist';
import {Login} from './Login';
import {Pages} from 'modules/constants/pages';
import {theme} from 'modules/theme';
import {GlobalStyle} from './GlobalStyle';
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
      client.clearStore();
      client.stop();
      login.disableSession();
    }

    console.error(error);
  },
});

const App: React.FC = () => {
  return (
    <ApolloProvider client={client}>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <BrowserRouter>
          <Switch>
            <Route path={Pages.Login} component={Login} />
            <PrivateRoute
              redirectPath={Pages.Login}
              path={Pages.Initial({useKeyParam: true})}
              component={Tasklist}
            />
          </Switch>
        </BrowserRouter>
      </ThemeProvider>
    </ApolloProvider>
  );
};

export {App};
