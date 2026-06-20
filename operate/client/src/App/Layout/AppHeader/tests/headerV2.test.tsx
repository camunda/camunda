/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AppHeader} from '../index';
import {render, screen} from 'modules/testing-library';
import {Wrapper} from './mocks';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

vi.mock('modules/feature-flags', async (importActual) => ({
  ...(await importActual()),
  IS_NAV_V2_ENABLED: true,
}));

describe('AppHeader (nav V2)', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser({authorizedComponents: ['operate']}));
  });

  it('should render the V2 sidebar navigation items', async () => {
    render(<AppHeader />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('link', {name: /dashboard/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /processes/i})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /decisions/i})).toBeInTheDocument();
  });

  it('should render the Operations group parent (V2-only)', async () => {
    render(<AppHeader />, {wrapper: Wrapper});

    expect(await screen.findByText('Operations')).toBeInTheDocument();
  });
});
