/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {noop} from 'lodash';

import Option from './index';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

const CHILD_CONTENT = 'I am a label';
const Child: React.FC = () => <span>{CHILD_CONTENT}</span>;

describe('Option', () => {
  it('should render a button if no children are passed', () => {
    render(<Option onClick={noop} />, {wrapper: ThemeProvider});

    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('should render passed children', () => {
    render(
      <Option onClick={noop}>
        <Child />
      </Option>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(CHILD_CONTENT)).toBeInTheDocument();
  });

  it('should handle click event', () => {
    const onClickMock = jest.fn();
    const mockOnStateChange = jest.fn();
    const {rerender} = render(
      <Option
        onClick={onClickMock}
        onStateChange={mockOnStateChange}
        disabled={true}
      />,
      {wrapper: ThemeProvider}
    );

    userEvent.click(screen.getByRole('button'));

    expect(onClickMock).not.toHaveBeenCalled();
    expect(mockOnStateChange).not.toHaveBeenCalled();

    rerender(
      <Option onClick={onClickMock} onStateChange={mockOnStateChange} />
    );

    userEvent.click(screen.getByRole('button'));

    expect(onClickMock).toHaveBeenCalled();
    expect(mockOnStateChange).toHaveBeenCalledWith({isOpen: false});
  });
});
