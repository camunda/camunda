/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'common/mocks/current-user';
import * as licenseMocks from 'common/mocks/license';

describe('App switcher', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.saasLicense);
        },
        {
          once: true,
        },
      ),
    );
  });

  it('should not render links for CCSM', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUserWithC8Links);
        },
        {
          once: true,
        },
      ),
    );

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByRole('link', {name: 'Console'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Modeler'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Operate'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Tasklist'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Optimize'}),
    ).not.toBeInTheDocument();
  });
});
