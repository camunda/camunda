/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';

import {SortIcon} from './index';

describe('SortIcon', () => {
  it('should render an Up icon', () => {
    render(<SortIcon sortOrder="asc" />);
    expect(screen.getByTestId('asc-icon')).toBeInTheDocument();
  });

  it('should render a Down icon', () => {
    render(<SortIcon sortOrder="desc" />);
    expect(screen.getByTestId('desc-icon')).toBeInTheDocument();
  });
});
