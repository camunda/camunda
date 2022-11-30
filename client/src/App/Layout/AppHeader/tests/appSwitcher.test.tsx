/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {AppHeader} from '../index';
import {MemoryRouter} from 'react-router-dom';
import {authenticationStore} from 'modules/stores/authentication';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children} </MemoryRouter>
    </ThemeProvider>
  );
};

describe('App switcher', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should render with correct links', async () => {
    mockGetUser().withSuccess(
      createUser({
        c8Links: {
          operate: 'https://link-to-operate',
          tasklist: 'https://link-to-tasklist',
          modeler: 'https://link-to-modeler',
          console: 'https://link-to-console',
          optimize: 'https://link-to-optimize',
        },
      })
    );

    await authenticationStore.authenticate();
    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /app switcher/i,
      })
    );

    const withinAppPanel = within(
      screen.getByRole('navigation', {
        name: /app panel/i,
      })
    );

    expect(
      await withinAppPanel.findByRole('link', {name: 'Console'})
    ).toHaveAttribute('href', 'https://link-to-console');
    expect(withinAppPanel.getByRole('link', {name: 'Modeler'})).toHaveAttribute(
      'href',
      'https://link-to-modeler'
    );
    expect(
      withinAppPanel.getByRole('link', {name: 'Tasklist'})
    ).toHaveAttribute('href', 'https://link-to-tasklist');
    expect(withinAppPanel.getByRole('link', {name: 'Operate'})).toHaveAttribute(
      'href',
      '/'
    );
    expect(
      withinAppPanel.getByRole('link', {name: 'Optimize'})
    ).toHaveAttribute('href', 'https://link-to-optimize');
  });
});
