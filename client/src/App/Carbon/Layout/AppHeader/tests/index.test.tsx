/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {AppHeader} from '../index';
import {render, screen, within} from 'modules/testing-library';
import {Wrapper} from './mocks';

describe('Header', () => {
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
        })
      ).getByRole('link', {
        name: /processes/i,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/carbon\/processes$/
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/
    );

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /dashboard/i,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/carbon$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /decisions/i,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/carbon\/decisions$/
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });
});
