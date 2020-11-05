/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import ProgressBar from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('ProgressBar', () => {
  it('should render 0% progress (count: 0)', () => {
    render(<ProgressBar totalCount={0} finishedCount={0} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '0%');
  });

  it('should render 0% progress (count: 5)', () => {
    render(<ProgressBar totalCount={5} finishedCount={0} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '0%');
  });

  it('should render 33% progress', () => {
    render(<ProgressBar totalCount={9} finishedCount={3} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '33%');
  });

  it('should render 100% progress', () => {
    render(<ProgressBar totalCount={5} finishedCount={5} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '100%');
  });
});
