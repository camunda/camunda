/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, act} from 'modules/testing-library';
import {AsyncActionTrigger} from '.';

describe('<AsyncActionTrigger />', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('should render children when status is idle', () => {
    render(
      <AsyncActionTrigger status="idle">
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
    expect(screen.queryByText('Pending...')).not.toBeInTheDocument();
  });

  it('should render default pending, success and error labels', () => {
    const {rerender} = render(
      <AsyncActionTrigger status="pending">
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Pending...')).toBeInTheDocument();

    rerender(
      <AsyncActionTrigger status="success">
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Successful!')).toBeInTheDocument();

    rerender(
      <AsyncActionTrigger status="error">
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Failed!')).toBeInTheDocument();
  });

  it('should render custom labels', () => {
    const {rerender} = render(
      <AsyncActionTrigger status="pending" pendingLabel="Loading...">
        <button>Retry</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Loading...')).toBeInTheDocument();

    rerender(
      <AsyncActionTrigger status="success" successLabel="Done!">
        <button>Retry</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Done!')).toBeInTheDocument();

    rerender(
      <AsyncActionTrigger status="error" errorLabel="Error!">
        <button>Retry</button>
      </AsyncActionTrigger>,
    );
    expect(screen.getByText('Error!')).toBeInTheDocument();
  });

  it('should call onReset after the default delay when status is success', () => {
    vi.useFakeTimers();
    const onReset = vi.fn();

    render(
      <AsyncActionTrigger status="success" onReset={onReset}>
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    act(() => {
      vi.advanceTimersByTime(1999);
    });
    expect(onReset).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it('should call onReset after the default delay when status is error', () => {
    vi.useFakeTimers();
    const onReset = vi.fn();

    render(
      <AsyncActionTrigger status="error" onReset={onReset}>
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it('should use custom reset delays', () => {
    vi.useFakeTimers();
    const onReset = vi.fn();

    render(
      <AsyncActionTrigger status="success" onReset={onReset} resetDelay={5000}>
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    act(() => {
      vi.advanceTimersByTime(4999);
    });
    expect(onReset).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it('does not call onReset for idle or pending status', () => {
    vi.useFakeTimers();
    const onReset = vi.fn();

    const {rerender} = render(
      <AsyncActionTrigger status="idle" onReset={onReset}>
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    rerender(
      <AsyncActionTrigger status="pending" onReset={onReset}>
        <button>Cancel</button>
      </AsyncActionTrigger>,
    );

    act(() => {
      vi.advanceTimersByTime(10000);
    });

    expect(onReset).not.toHaveBeenCalled();
  });
});
