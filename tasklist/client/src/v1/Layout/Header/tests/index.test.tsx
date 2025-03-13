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
