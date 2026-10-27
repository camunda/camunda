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

  it('should migrate deprecated query params on /processes routes', () => {
    render(
      <MemoryRouter
        initialEntries={['/processes?process=myProc&version=2&tenant=t1']}
      >
        <RedirectDeprecatedRoutes />
        <LocationLog />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /processDefinitionId=myProc/,
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      /processDefinitionVersion=2/,
    );
    expect(screen.getByTestId('search')).toHaveTextContent(/tenantId=t1/);
  });
});
