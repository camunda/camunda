/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Skeleton} from './index';

describe('<Skeleton />', () => {
  it('should render the correct amount of rows', () => {
    const rowCount = 10;
    render(<Skeleton rowsToDisplay={10} />, {wrapper: ThemeProvider});

    expect(
      screen.getAllByTestId('flow-node-instance-log-skeleton-row')
    ).toHaveLength(rowCount);
  });
});
