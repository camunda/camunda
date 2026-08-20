/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, act} from 'modules/testing-library';
import {CopyButton} from '.';

describe('<CopyButton />', () => {
  const mockWriteText = vi.fn();

  beforeEach(() => {
    mockWriteText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: {writeText: mockWriteText},
      configurable: true,
      writable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true,
      writable: true,
    });
  });

  it('should render the copy button with default label and icon', () => {
    render(<CopyButton value="some-value" />);

    expect(screen.getByRole('button', {name: 'Copy'})).toBeInTheDocument();
    expect(screen.getByText('Copy')).toBeInTheDocument();
  });

  it('should copy the value to clipboard when clicked', async () => {
    const {user} = render(<CopyButton value="hello world" />);

    await user.click(screen.getByRole('button', {name: 'Copy'}));

    expect(mockWriteText).toHaveBeenCalledWith('hello world');
  });

  it('should show copied feedback after clicking', async () => {
    const {user} = render(<CopyButton value="hello world" />);

    await user.click(screen.getByRole('button', {name: 'Copy'}));

    expect(screen.getByRole('button', {name: 'Copied'})).toBeInTheDocument();
    expect(screen.getByText('Copied')).toBeInTheDocument();
  });

  it('should reset to copy state after the feedback timeout', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {user} = render(<CopyButton value="hello world" />);

    await user.click(screen.getByRole('button', {name: 'Copy'}));
    expect(screen.getByText('Copied')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('Copy')).toBeInTheDocument();
  });

  it('should reset copied state when value prop changes', async () => {
    const {user, rerender} = render(<CopyButton value="first" />);

    await user.click(screen.getByRole('button', {name: 'Copy'}));
    expect(screen.getByText('Copied')).toBeInTheDocument();

    rerender(<CopyButton value="second" />);

    expect(screen.getByText('Copy')).toBeInTheDocument();
  });

  it('should cancel the feedback timeout when the value changes mid-countdown', async () => {
    const {user, rerender} = render(<CopyButton value="first" />);

    await user.click(screen.getByRole('button', {name: 'Copy'}));
    expect(screen.getByText('Copied')).toBeInTheDocument();

    // Switch to fake timers after the click so we control the pending timeout
    vi.useFakeTimers();

    rerender(<CopyButton value="second" />);
    expect(screen.getByText('Copy')).toBeInTheDocument();

    // Ensure the old timeout does not flip state back after the value has changed
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('Copy')).toBeInTheDocument();
  });
});
