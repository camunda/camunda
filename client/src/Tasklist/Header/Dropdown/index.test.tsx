/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {Dropdown} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';

describe('<Dropdown />', () => {
  it('should render dropdown', () => {
    render(<Dropdown />, {
      wrapper: MockThemeProvider,
    });
    expect(screen.getByText('Demo user')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should show&hide dropdown menu behavior works correctly', () => {
    render(
      <>
        <Dropdown />
        <div data-testid="some-other-element" />
      </>,
      {
        wrapper: MockThemeProvider,
      },
    );

    // menu should not be displayed on first load
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo user'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed after clicking dropdown again
    fireEvent.click(screen.getByText('Demo user'));
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo user'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed if another element is clicked
    fireEvent.click(screen.getByTestId('some-other-element'));
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo user'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed if esc key is pressed
    fireEvent.keyDown(screen.getByText('Demo user'), {
      key: 'Escape',
      code: 27,
    });
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();

    // menu should be displayed on dropdown click
    fireEvent.click(screen.getByText('Demo user'));
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // menu should not be displayed if one of the dropdown items are clicked
    fireEvent.click(screen.getByText('Logout'));
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });
});
