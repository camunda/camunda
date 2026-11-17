/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {SortOrderToggle} from './index';
import {instanceHistorySortOrderStore} from 'modules/stores/instanceHistorySortOrder';
import {clearStateLocally} from 'modules/utils/localStorage';

describe('SortOrderToggle', () => {
  beforeEach(() => {
    clearStateLocally();
    instanceHistorySortOrderStore.reset();
  });

  it('should show latest first by default', () => {
    render(<SortOrderToggle />);

    expect(
      screen.getByRole('button', {name: 'Latest first'}),
    ).toBeInTheDocument();
  });

  it('should toggle the order label when clicked', async () => {
    const {user} = render(<SortOrderToggle />);

    await user.click(screen.getByRole('button', {name: 'Latest first'}));

    expect(
      screen.getByRole('button', {name: 'Oldest first'}),
    ).toBeInTheDocument();
    expect(instanceHistorySortOrderStore.order).toBe('asc');

    await user.click(screen.getByRole('button', {name: 'Oldest first'}));

    expect(
      screen.getByRole('button', {name: 'Latest first'}),
    ).toBeInTheDocument();
    expect(instanceHistorySortOrderStore.order).toBe('desc');
  });
});
