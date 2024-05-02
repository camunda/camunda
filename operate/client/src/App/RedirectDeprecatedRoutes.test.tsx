/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';

describe('<RedirectDeprecatedRoutes />', () => {
  it('should replace old routes with new routes', () => {
    const {unmount} = render(
      <MemoryRouter initialEntries={['/instances?foo=bar']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?foo=bar$/);

    unmount();

    render(
      <MemoryRouter initialEntries={['/instances/1?foo=bar']}>
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/,
    );
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?foo=bar$/);
  });
});
