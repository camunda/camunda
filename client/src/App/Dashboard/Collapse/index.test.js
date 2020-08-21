/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {Collapse} from './index';

describe('<Collapse />', () => {
  it('should be collapsed by default', () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockButtonTitle = 'button-title';

    render(
      <Collapse
        content={mockContent}
        header={mockHeader}
        buttonTitle={mockButtonTitle}
      />
    );

    expect(screen.getByText(mockHeader)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: mockButtonTitle})
    ).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });

  it('should uncollapse and collapse', () => {
    const mockContent = 'mock-content';
    const mockHeader = 'mock-header';
    const mockButtonTitle = 'button-title';

    render(
      <Collapse
        content={mockContent}
        header={mockHeader}
        buttonTitle={mockButtonTitle}
      />
    );

    fireEvent.click(screen.getByRole('button', {name: mockButtonTitle}));

    expect(screen.getByText(new RegExp(mockContent))).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: mockButtonTitle}));

    expect(screen.queryByText(new RegExp(mockContent))).not.toBeInTheDocument();
  });
});
