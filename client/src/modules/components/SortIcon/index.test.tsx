/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';

import SortIcon from './index';
import {SORT_ORDER} from 'modules/constants';

describe('SortIcon', () => {
  it('should render an Up icon', () => {
    render(<SortIcon sortOrder={SORT_ORDER.ASC} />);
    expect(screen.getByTestId(`${SORT_ORDER.ASC}-icon`)).toBeInTheDocument();
  });

  it('should render a Down icon', () => {
    render(<SortIcon sortOrder={SORT_ORDER.DESC} />);
    expect(screen.getByTestId(`${SORT_ORDER.DESC}-icon`)).toBeInTheDocument();
  });

  it('should render a Down icon by default', () => {
    render(<SortIcon />);
    expect(screen.getByTestId('sort-icon')).toBeInTheDocument();
  });
});
