/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Textarea from './index';

describe('Textarea', () => {
  it('should render default textarea', () => {
    render(<Textarea placeholder="someLabel" />, {wrapper: ThemeProvider});

    expect(
      screen.getByRole('textbox', {name: 'someLabel'})
    ).toBeInTheDocument();
  });

  it('should render autosize textarea', () => {
    render(<Textarea placeholder="someLabel" hasAutoSize />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByRole('textbox', {name: 'someLabel'})
    ).toBeInTheDocument();
  });
});
