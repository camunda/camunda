/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import * as licenseMocks from 'modules/mock-schema/mocks/license';

describe('license note', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  beforeEach(() => {
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
  });

  it('should show and hide license information', async () => {
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

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(screen.getByText('Non-production license'));

    expect(
      screen.getByText(
        /Non-production license. For production usage details, visit/,
      ),
    ).toBeInTheDocument();
  });

  it('should show license note in CCSM free/trial environment', async () => {
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
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Non-production license'),
    ).toBeInTheDocument();
  });

  it('should not show license note in SaaS environment', async () => {
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
    window.clientConfig = {
      isEnterprise: false,
      organizationId: '000000000-0000-0000-0000-000000000000',
    };

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      screen.queryByText('Non-production license'),
    ).not.toBeInTheDocument();
  });

  it('should show production license note in CCSM enterprise environment', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.validLicense);
        },
        {
          once: true,
        },
      ),
    );

    window.clientConfig = {
      isEnterprise: true,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText('Production license')).toBeInTheDocument();
  });

  it('should hide commercial license note in self-managed if license is commercial', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.commercialExpired);
        },
        {
          once: true,
        },
      ),
    );

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      screen.queryByText(/^Non-commercial license - expired$/i),
    ).not.toBeInTheDocument();
  });

  it('should show non-commercial license expiry date', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.validNonCommercial);
        },
        {
          once: true,
        },
      ),
    );

    render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      await screen.findByText(/^Non-commercial license - 0 day left$/i),
    ).toBeInTheDocument();
  });
});
