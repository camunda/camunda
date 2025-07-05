/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {HelperModal} from '.';
import {getStateLocally} from 'modules/utils/localStorage';

const localStorageKey = 'myStorageKey';

describe('HelperModal', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('should render modal content', async () => {
    const onCloseMock = vi.fn();
    const onSubmitMock = vi.fn();

    render(
      <HelperModal
        onClose={onCloseMock}
        onSubmit={onSubmitMock}
        localStorageKey={localStorageKey}
        open={true}
        title="My Helper Modal"
      >
        Modal Content
      </HelperModal>,
    );

    expect(screen.getByText('My Helper Modal')).toBeInTheDocument();
    expect(screen.getByText('Modal Content')).toBeInTheDocument();
  });

  it('should call onClose and onSubmit callbacks', () => {
    const onCloseMock = vi.fn();
    const onSubmitMock = vi.fn();

    render(
      <HelperModal
        onClose={onCloseMock}
        onSubmit={onSubmitMock}
        localStorageKey={localStorageKey}
        open={true}
        title="My Helper Modal"
      >
        Modal Content
      </HelperModal>,
    );

    screen.getByRole('button', {name: /continue/i}).click();
    expect(onSubmitMock).toHaveBeenCalledTimes(1);

    screen.getByRole('button', {name: /cancel/i}).click();
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('should set local storage key', () => {
    const onCloseMock = vi.fn();
    const onSubmitMock = vi.fn();

    render(
      <HelperModal
        onClose={onCloseMock}
        onSubmit={onSubmitMock}
        localStorageKey={localStorageKey}
        open={true}
        title="My Helper Modal"
      >
        Modal Content
      </HelperModal>,
    );

    expect(getStateLocally()[localStorageKey]).toBe(undefined);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(true);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(false);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(true);
  });
});
