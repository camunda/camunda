/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import CollapseButton from './index';

describe('<CollapseButton />', () => {
  it('should handle click events', () => {
    const onClick = jest.fn();
    render(<CollapseButton onClick={onClick} direction="UP" />, {
      wrapper: ThemeProvider,
    });

    userEvent.click(screen.getByRole('button'));

    expect(onClick).toHaveBeenCalled();
  });

  it('should render the correct icon', () => {
    const {rerender} = render(<CollapseButton direction="UP" />, {
      wrapper: ThemeProvider,
    });

    expect(screen.getByTestId('icon-up')).toBeInTheDocument();
    expect(screen.queryByTestId('icon-down')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-left')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-right')).not.toBeInTheDocument();

    rerender(<CollapseButton direction="DOWN" />);

    expect(screen.getByTestId('icon-down')).toBeInTheDocument();
    expect(screen.queryByTestId('icon-up')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-left')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-right')).not.toBeInTheDocument();

    rerender(<CollapseButton direction="LEFT" />);

    expect(screen.getByTestId('icon-left')).toBeInTheDocument();
    expect(screen.queryByTestId('icon-up')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-down')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-right')).not.toBeInTheDocument();

    rerender(<CollapseButton direction="RIGHT" />);

    expect(screen.getByTestId('icon-right')).toBeInTheDocument();
    expect(screen.queryByTestId('icon-up')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-down')).not.toBeInTheDocument();
    expect(screen.queryByTestId('icon-left')).not.toBeInTheDocument();
  });
});
