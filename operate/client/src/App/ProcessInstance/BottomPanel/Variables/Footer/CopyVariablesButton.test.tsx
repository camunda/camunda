/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from '@testing-library/react';
import {CopyVariablesButton} from './CopyVariablesButton';
import {render} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {createVariable} from 'modules/testUtils';

describe('CopyVariableButton', () => {
  it('should be disabled (no variables)', () => {
    render(<CopyVariablesButton />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (too many variables)', () => {
    const variables = [...Array(50)].map((_, i) =>
      createVariable({name: i.toString()}),
    );

    variablesStore.setItems(variables);

    render(<CopyVariablesButton />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (truncated values)', () => {
    variablesStore.setItems([
      createVariable({
        isPreview: true,
      }),
    ]);

    render(<CopyVariablesButton />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should copy variables to clipboard', async () => {
    const writeTextSpy = jest.spyOn(navigator.clipboard, 'writeText');

    variablesStore.setItems([
      createVariable({
        name: 'jsonVariable',
        value: JSON.stringify({a: 123, b: [1, 2, 3], c: 'text'}),
      }),
      createVariable({
        name: 'numberVariable',
        value: '666',
      }),
      createVariable({
        name: 'stringVariable',
        value: '"text"',
      }),
    ]);

    const {user} = render(<CopyVariablesButton />);

    expect(screen.getByRole('button')).toBeEnabled();
    await user.click(screen.getByRole('button'));

    expect(writeTextSpy).toHaveBeenCalledWith(
      '{"jsonVariable":{"a":123,"b":[1,2,3],"c":"text"},"numberVariable":666,"stringVariable":"text"}',
    );
  });
});
