/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';

describe('<RedirectDeprecatedRoutes />', () => {
  it('should replace hash routes', () => {
    render(
      <MemoryRouter initialEntries={['#/']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
  });

  it('should replace hash routes and old routes with new routes', () => {
    const {unmount} = render(
      <MemoryRouter initialEntries={['/#/instances']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);

    unmount();

    render(
      <MemoryRouter initialEntries={['#/instances']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
  });

  it('should replace old routes with new routes', () => {
    const {unmount} = render(
      <MemoryRouter initialEntries={['/instances?foo=bar']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?foo=bar$/);

    unmount();

    render(
      <MemoryRouter initialEntries={['/instances/1?foo=bar']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/
    );
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?foo=bar$/);
  });
});
