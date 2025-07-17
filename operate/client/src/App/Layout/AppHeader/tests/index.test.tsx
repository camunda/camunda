/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AppHeader} from '../index';
import {render, screen, within} from 'modules/testing-library';
import {Wrapper} from './mocks';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

describe('Header', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
  });

  it('should go to the correct pages when clicking on header links', async () => {
    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).getByRole('link', {
        name: /processes/i,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/,
    );

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).getByRole('link', {
        name: /dashboard/i,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).getByRole('link', {
        name: /decisions/i,
      }),
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/,
    );
  });
});
