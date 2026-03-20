/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  availableErrorTypes,
  getIncidentErrorName,
} from 'modules/utils/incidents';
import {IncidentsFilter} from './index';
import {render, screen, within} from 'modules/testing-library';

describe('IncidentsFilter', () => {
  it('should render filters', async () => {
    const {user} = render(<IncidentsFilter />);

    await user.click(
      screen.getByRole('combobox', {name: /filter by incident type/i}),
    );

    for (const errorType of availableErrorTypes) {
      expect(
        screen.getByRole('option', {name: getIncidentErrorName(errorType)}),
      ).toBeInTheDocument();
    }
  });

  it('should render the source filter dropdown with all options', async () => {
    const {user} = render(<IncidentsFilter />);

    // Default shows "All sources"
    expect(screen.getByTestId('incidents-by-source')).toHaveTextContent(
      'All sources',
    );

    // Open the dropdown by clicking the combobox trigger
    await user.click(
      within(screen.getByTestId('incidents-by-source')).getByRole('combobox'),
    );

    // Verify all 3 options are available
    expect(screen.getByRole('option', {name: 'All sources'})).toBeInTheDocument();
    expect(screen.getByRole('option', {name: 'Global'})).toBeInTheDocument();
    expect(screen.getByRole('option', {name: 'Model'})).toBeInTheDocument();
  });

  it('should disable/enable clear all button depending on selected options', async () => {
    const {user} = render(<IncidentsFilter />);

    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();

    await user.click(
      screen.getByRole('combobox', {name: /filter by incident type/i}),
    );

    await user.click(
      screen.getByRole('option', {
        name: 'Condition error.',
      }),
    );
    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeEnabled();

    expect(
      screen.getByRole('option', {
        name: 'Condition error.',
        selected: true,
      }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Reset Filters'}));

    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();
  });
});
