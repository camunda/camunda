/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import ExpandButton from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('ExpandButton', () => {
  it('should render arrow icon', () => {
    render(
      // @ts-expect-error ts-migrate(2322) FIXME: Property 'iconButtonTheme' does not exist on type ... Remove this comment to see the full error message
      <ExpandButton isExpanded={false} iconButtonTheme="default" />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('arrow-icon')).toBeInTheDocument();
  });

  it('should render provided children inside the button', () => {
    render(
      // @ts-expect-error ts-migrate(2322) FIXME: Property 'iconButtonTheme' does not exist on type ... Remove this comment to see the full error message
      <ExpandButton iconButtonTheme="default">
        <div id="child1">child node 1</div>
        <div id="child2">child node 2</div>
      </ExpandButton>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('child node 1')).toBeInTheDocument();
    expect(screen.getByText('child node 2')).toBeInTheDocument();
  });
});
