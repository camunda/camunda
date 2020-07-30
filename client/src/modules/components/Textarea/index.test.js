/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Textarea from './index';
import {render, screen} from '@testing-library/react';

describe('Textarea', () => {
  it('should render default textarea', () => {
    render(<Textarea placeholder="someLabel" />);

    expect(
      screen.getByRole('textbox', {name: 'someLabel'})
    ).toBeInTheDocument();
  });

  it('should render autosize textarea', () => {
    render(<Textarea placeholder="someLabel" hasAutosize />);

    expect(
      screen.getByRole('textbox', {name: 'someLabel'})
    ).toBeInTheDocument();
  });
});
