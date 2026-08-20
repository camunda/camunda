/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {vi} from 'vitest';

vi.mock('modules/featureFlags', () => ({IS_NAV_V2_ENABLED: true}));

import {render, screen} from 'modules/testing/testing-library';
import {nodeMockServer} from 'modules/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import {currentUser, invalidLicense} from '@camunda/c8-mocks';

describe('<Header /> (V2)', () => {
  it('should render a header', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(currentUser);
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
          return HttpResponse.json(invalidLicense);
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
      await screen.findByRole('banner', {name: 'Camunda Tasklist'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByText('Non-production license'),
    ).toBeInTheDocument();
    expect(screen.getByText('Non-commercial license')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Info'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Settings'})).toBeInTheDocument();
  });
});
