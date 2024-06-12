/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

describe('<Header />', () => {
  it('should render a header', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
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
    expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Open Info'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Open Settings'}),
    ).toBeInTheDocument();
  });
});
