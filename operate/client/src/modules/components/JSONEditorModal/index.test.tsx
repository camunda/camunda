/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {JSONEditorModal} from './index';
import {render, screen} from 'modules/testing-library';

describe('<JSONEditorModal />', () => {
  it('should not render the modal', () => {
    render(<JSONEditorModal isVisible={false} value="" />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should render modal', () => {
    const mockValue = '"fooo"';
    const mockTitle = 'i am a title';

    const {rerender} = render(
      <JSONEditorModal isVisible value={mockValue} title={mockTitle} />,
    );

    expect(
      screen.getByRole('heading', {
        name: mockTitle,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();

    rerender(
      <JSONEditorModal
        isVisible
        readOnly
        value={mockValue}
        title={mockTitle}
      />,
    );

    expect(
      screen.getByRole('heading', {
        name: mockTitle,
      }),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = jest.fn();

    const {user, rerender} = render(
      <JSONEditorModal isVisible value="" onClose={mockOnClose} />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    rerender(
      <JSONEditorModal isVisible readOnly value="" onClose={mockOnClose} />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('should handle modal apply', async () => {
    const mockOnApply = jest.fn();
    const mockValue = '"i am a value"';

    const {user} = render(
      <JSONEditorModal isVisible value={mockValue} onApply={mockOnApply} />,
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenNthCalledWith(1, mockValue);
  });

  it('should handle value edit', async () => {
    const mockOnApply = jest.fn();
    const mockInitialValue = '"i am a value"';
    const mockUpdatedValue = '"i am an updated value"';

    const {user} = render(
      <JSONEditorModal
        isVisible
        value={mockInitialValue}
        onApply={mockOnApply}
      />,
    );

    const editor = screen.getByDisplayValue(mockInitialValue);

    await user.clear(editor);
    await user.type(editor, mockUpdatedValue);
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenNthCalledWith(1, mockUpdatedValue);
  });
});
