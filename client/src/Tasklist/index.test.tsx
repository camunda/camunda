/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';

import {Tasklist} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';

const Wrapper: React.FC = ({children}) => {
  return (
    <MemoryRouter>
      <MockThemeProvider>{children}</MockThemeProvider>
    </MemoryRouter>
  );
};

describe('<Tasklist />', () => {
  it('should render', () => {
    render(<Tasklist />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Tasklist')).toBeInTheDocument();
  });
});
