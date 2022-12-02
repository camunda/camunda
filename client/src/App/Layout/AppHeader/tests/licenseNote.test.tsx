/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {AppHeader} from '../index';
import {render, screen, within} from 'modules/testing-library';
import {Wrapper} from './mocks';

describe('license note', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should show and hide license information', async () => {
    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(
      await within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).findByRole('link', {
        name: /dashboard/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: 'Non-Production License',
        expanded: false,
      })
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Non-Production License'})
    );

    expect(
      screen.getByRole('button', {
        name: 'Non-Production License',
        expanded: true,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /Non-Production License. If you would like information on production usage, please refer to our/
      )
    ).toBeInTheDocument();
  });

  it('should show license note in CCSM free/trial environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('Non-Production License')
    ).toBeInTheDocument();
  });

  it('should not show license note in SaaS environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: '000000000-0000-0000-0000-000000000000',
    };

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(
      await within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).findByRole('link', {
        name: /dashboard/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.queryByText('Non-Production License')
    ).not.toBeInTheDocument();
  });

  it('should not show license note in CCSM enterprise environment', async () => {
    window.clientConfig = {
      isEnterprise: true,
      organizationId: null,
    };

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(
      await within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).findByRole('link', {
        name: /dashboard/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.queryByText('Non-Production License')
    ).not.toBeInTheDocument();
  });
});
