/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import ExpandButton from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('ExpandButton', () => {
  it('should render arrow icon', () => {
    render(
      <ExpandButton
        isExpanded={false}
        iconButtonTheme="default"
        onClick={() => {}}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByTestId('arrow-icon')).toBeInTheDocument();
  });

  it('should render provided children inside the button', () => {
    render(
      <ExpandButton
        isExpanded={false}
        iconButtonTheme="default"
        onClick={() => {}}
      >
        <div id="child1">child node 1</div>
        <div id="child2">child node 2</div>
      </ExpandButton>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('child node 1')).toBeInTheDocument();
    expect(screen.getByText('child node 2')).toBeInTheDocument();
  });
});
