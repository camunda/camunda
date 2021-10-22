/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import SplitPane from './index';

const FirstChild: React.FC = () => <div>First child</div>;
const SecondChild: React.FC = () => <div>Second child</div>;

describe('SplitPane', () => {
  it('should render panels', () => {
    render(
      <SplitPane>
        <SplitPane.Pane>
          <FirstChild />
        </SplitPane.Pane>
        <SplitPane.Pane>
          <SecondChild />
        </SplitPane.Pane>
      </SplitPane>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('First child')).toBeInTheDocument();
    expect(screen.getByText('Second child')).toBeInTheDocument();
    expect(screen.getByTestId('icon-up')).toBeInTheDocument();
    expect(screen.getByTestId('icon-down')).toBeInTheDocument();
  });

  it('should handle expansion change', () => {
    render(
      <SplitPane>
        <SplitPane.Pane>
          <FirstChild />
        </SplitPane.Pane>
        <SplitPane.Pane>
          <SecondChild />
        </SplitPane.Pane>
      </SplitPane>,
      {wrapper: ThemeProvider}
    );

    userEvent.click(screen.getByTestId('icon-up'));

    expect(screen.queryByTestId('icon-up')).not.toBeInTheDocument();
    expect(screen.getByTestId('icon-down')).toBeInTheDocument();

    userEvent.click(screen.getByTestId('icon-down'));

    expect(screen.getByTestId('icon-up')).toBeInTheDocument();
    expect(screen.getByTestId('icon-down')).toBeInTheDocument();

    userEvent.click(screen.getByTestId('icon-down'));

    expect(screen.queryByTestId('icon-down')).not.toBeInTheDocument();
    expect(screen.getByTestId('icon-up')).toBeInTheDocument();

    userEvent.click(screen.getByTestId('icon-up'));

    expect(screen.getByTestId('icon-up')).toBeInTheDocument();
    expect(screen.getByTestId('icon-down')).toBeInTheDocument();
  });
});
