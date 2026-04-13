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

  it('should render modal', async () => {
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
    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /copy/i})).toBeInTheDocument();

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
    expect(screen.getByRole('button', {name: /copy/i})).toBeInTheDocument();
  });

  it('should handle modal close', async () => {
    const mockOnClose = vi.fn();

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
    const mockOnApply = vi.fn();
    const mockValue = '"i am a value"';

    const {user} = render(
      <JSONEditorModal isVisible value={mockValue} onApply={mockOnApply} />,
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnApply).toHaveBeenNthCalledWith(1, mockValue);
  });

  it('should handle value edit', async () => {
    const mockOnApply = vi.fn();
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

  it('should copy value to clipboard when copy button is clicked', async () => {
    const mockValue = '"i am a value"';
    const mockWriteText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: {writeText: mockWriteText},
      writable: true,
    });

    const {user} = render(<JSONEditorModal isVisible value={mockValue} />);

    await user.click(screen.getByRole('button', {name: /^copy$/i}));

    expect(mockWriteText).toHaveBeenCalledWith(mockValue);
    expect(
      await screen.findByRole('button', {name: /copied/i}),
    ).toBeInTheDocument();
  });

  it('should copy value to clipboard in read-only mode', async () => {
    const mockValue = '"i am a read-only value"';
    const mockWriteText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: {writeText: mockWriteText},
      writable: true,
    });

    const {user} = render(
      <JSONEditorModal isVisible readOnly value={mockValue} />,
    );

    await user.click(screen.getByRole('button', {name: /^copy$/i}));

    expect(mockWriteText).toHaveBeenCalledWith(mockValue);
    expect(
      await screen.findByRole('button', {name: /copied/i}),
    ).toBeInTheDocument();
  });

  it('should not show mode toggle button when allowModeToggle is not set', () => {
    const mockValue = '"i am a value"';

    render(<JSONEditorModal isVisible readOnly value={mockValue} />);

    expect(
      screen.queryByRole('button', {name: /^edit$/i}),
    ).not.toBeInTheDocument();
  });

  it('should show edit button in read-only mode when allowModeToggle is set', async () => {
    const mockValue = '"i am a value"';
    const mockOnApply = vi.fn();

    render(
      <JSONEditorModal
        isVisible
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
      />,
    );

    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^edit$/i})).toBeInTheDocument();
  });

  it('should switch from read-only to edit mode when edit button is clicked', async () => {
    const mockValue = '"i am a value"';
    const mockOnApply = vi.fn();
    const mockTitle = 'View mode title';
    const mockEditTitle = 'Edit mode title';

    const {user} = render(
      <JSONEditorModal
        isVisible
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
        title={mockTitle}
        editModeTitle={mockEditTitle}
      />,
    );

    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('heading', {name: mockTitle})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^edit$/i}));

    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^edit$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^view$/i})).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: mockEditTitle}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^view$/i}));

    expect(screen.getByRole('heading', {name: mockTitle})).toBeInTheDocument();
  });

  it('should switch back to view mode when view button is clicked', async () => {
    const mockValue = '"i am a value"';
    const mockOnApply = vi.fn();

    const {user} = render(
      <JSONEditorModal
        isVisible
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
      />,
    );

    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^edit$/i}));
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^view$/i}));

    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^edit$/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^view$/i}),
    ).not.toBeInTheDocument();
  });

  it('should reset to read-only mode after modal is closed and reopened', async () => {
    const mockValue = '"i am a value"';
    const mockOnApply = vi.fn();
    const mockOnClose = vi.fn();

    const {user, rerender} = render(
      <JSONEditorModal
        isVisible
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
        onClose={mockOnClose}
      />,
    );

    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^edit$/i}));
    expect(screen.getByRole('button', {name: /apply/i})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));
    expect(mockOnClose).toHaveBeenCalledTimes(1);

    rerender(
      <JSONEditorModal
        isVisible={false}
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
        onClose={mockOnClose}
      />,
    );

    rerender(
      <JSONEditorModal
        isVisible
        readOnly
        allowModeToggle
        value={mockValue}
        onApply={mockOnApply}
        onClose={mockOnClose}
      />,
    );

    expect(await screen.findByDisplayValue(mockValue)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /apply/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^edit$/i})).toBeInTheDocument();
  });
});
