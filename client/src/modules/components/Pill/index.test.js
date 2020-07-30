/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import Pill from './index';
import {PILL_TYPE} from 'modules/constants';

describe('Pill', () => {
  const labelString = 'Some Label';

  it('should render label', () => {
    render(<Pill isActive={true}>{labelString}</Pill>);
    expect(screen.getByText(labelString)).toBeInTheDocument();
  });

  it('should render without icon if no type is passed', () => {
    render(<Pill isActive={true}>{labelString}</Pill>);
    expect(screen.queryByTestId('target-icon')).not.toBeInTheDocument();
  });

  it('should render without icon if an unknown type is passed', () => {
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();

    render(
      <Pill isActive={true} type={'someUnknownType'}>
        {labelString}
      </Pill>
    );
    expect(screen.queryByTestId('target-icon')).not.toBeInTheDocument();
    global.console.error = originalConsoleError;
  });

  it('should render with icon', () => {
    render(
      <Pill isActive={true} type={PILL_TYPE.TIMESTAMP}>
        {labelString}
      </Pill>
    );

    expect(screen.getByTestId('target-icon')).toBeInTheDocument();
  });

  it('should render count for filter type pills', () => {
    render(
      <Pill isActive={true} type={PILL_TYPE.FILTER} count={10}>
        {labelString}
      </Pill>
    );
    expect(screen.getByText('10')).toBeInTheDocument();
  });
});
