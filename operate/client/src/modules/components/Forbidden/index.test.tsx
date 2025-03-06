/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';
import {render, screen} from 'modules/testing-library';
import {Forbidden} from './index';

jest.mock('App/AuthenticationCheck', () => ({
  AuthenticationCheck: ({children}: {children: ReactNode}) => <>{children}</>,
}));

jest.mock('App/Layout/AppHeader', () => ({
  AppHeader: () => <div data-testid="app-header">App Header</div>,
}));

describe('Forbidden', () => {
  it('should render Forbidden component with correct text and link', async () => {
    render(<Forbidden />);

    expect(screen.getByTestId('app-header')).toBeInTheDocument();
    expect(
      screen.getByText('You don’t have access to this component'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: /Learn more about roles and permissions/i,
      }),
    ).toBeInTheDocument();
  });
});
