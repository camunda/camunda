/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import EmptyPanel, {WithRowCount} from './index';
import PropTypes from 'prop-types';

const label = 'someLabel';
const SkeletonMock = ({rowsToDisplay}) => (
  <div data-testid="Skeleton">{rowsToDisplay}</div>
);

SkeletonMock.propTypes = {
  rowsToDisplay: PropTypes.number,
};

describe('EmptyPanel', () => {
  it('should display a warning message', () => {
    render(<EmptyPanel label={label} type="warning" />, {
      wrapper: ThemeProvider,
    });

    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.getByTestId('warning-icon')).toBeInTheDocument();
  });

  it('should display a success message', () => {
    render(<EmptyPanel label={label} type="info" />, {wrapper: ThemeProvider});
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.queryByTestId('warning-icon')).not.toBeInTheDocument();
  });

  it('should display a skeleton', () => {
    const containerRef = {current: {clientHeight: 200}};

    render(
      <EmptyPanel
        containerRef={containerRef}
        rowHeight={12}
        label={label}
        type="skeleton"
        Skeleton={SkeletonMock}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('Skeleton')).toBeInTheDocument();
  });

  it('should calculate number of shown skeleton rows', () => {
    const containerRef = {current: {clientHeight: 200}};

    render(
      <WithRowCount rowHeight={12} containerRef={containerRef}>
        <SkeletonMock />
      </WithRowCount>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('16')).toBeInTheDocument();
  });
});
