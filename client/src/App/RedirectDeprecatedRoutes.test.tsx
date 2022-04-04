/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';

describe('<RedirectDeprecatedRoutes />', () => {
  it('should replace hash routes', () => {
    render(
      <MemoryRouter initialEntries={['/#/']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
  });

  it('should replace /instances routes to /processes', () => {
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
