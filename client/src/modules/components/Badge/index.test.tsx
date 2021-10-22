/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import Badge from './index';

describe('<Badge />', () => {
  it('should contain passed number', () => {
    const CONTENT = '123';
    render(<Badge>{CONTENT}</Badge>, {wrapper: ThemeProvider});

    expect(screen.getByText(CONTENT)).toBeInTheDocument();
  });
});
