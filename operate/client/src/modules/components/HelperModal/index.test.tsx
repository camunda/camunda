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

let onCloseMock: jest.Mock;
let onSubmitMock: jest.Mock;

describe('HelperModal', () => {
  beforeAll(() => {
    onCloseMock = jest.fn();
    onSubmitMock = jest.fn();
  });

  beforeEach(() => {
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
  });

  afterEach(() => {
    onCloseMock.mockClear();
    onSubmitMock.mockClear();
    localStorage.clear();
  });

  it('should render modal content', async () => {
    expect(screen.getByText('My Helper Modal')).toBeInTheDocument();
    expect(screen.getByText('Modal Content')).toBeInTheDocument();
  });

  it('should call onClose and onSubmit callbacks', () => {
    screen.getByRole('button', {name: /continue/i}).click();
    expect(onSubmitMock).toHaveBeenCalledTimes(1);

    screen.getByRole('button', {name: /cancel/i}).click();
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('should set local storage key', () => {
    expect(getStateLocally()[localStorageKey]).toBe(undefined);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(true);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(false);

    screen.getByRole('checkbox').click();
    expect(getStateLocally()[localStorageKey]).toBe(true);
  });
});
