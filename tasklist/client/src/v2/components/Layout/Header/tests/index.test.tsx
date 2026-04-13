/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'v2/testing/testing-library';
import {nodeMockServer} from 'v2/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'v2/mocks/current-user';
import * as licenseMocks from 'v2/mocks/license';

describe('<Header />', () => {
  it('should render a header', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.invalidLicense);
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
      screen.getByRole('banner', {name: 'Camunda Tasklist'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByText('Non-production license'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Open Info'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Open Settings'}),
    ).toBeInTheDocument();
  });
});
