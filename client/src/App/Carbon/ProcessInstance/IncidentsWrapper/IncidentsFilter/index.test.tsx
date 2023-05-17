/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsFilter} from './index';
import {render, screen} from 'modules/testing-library';

import {mockIncidents} from './index.setup';
import {incidentsStore} from 'modules/stores/incidents';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {useEffect} from 'react';

const {reset, fetchIncidents} = incidentsStore;

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return reset;
  }, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('IncidentsFilter', () => {
  it('should render filters', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    const {user} = render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });

    await user.click(
      screen.getByRole('button', {name: /filter by incident type/i})
    );
    expect(
      screen.getByRole('option', {name: 'Condition error'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {name: 'Extract value error'})
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /filter by flow node/i})
    );
    expect(
      screen.getByRole('option', {name: 'flowNodeId_exclusiveGateway'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {name: 'flowNodeId_alwaysFailingTask'})
    ).toBeInTheDocument();
  });

  it('should disable/enable clear all button depending on selected options', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    const {user} = render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });

    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();

    await user.click(
      screen.getByRole('button', {name: /filter by incident type/i})
    );

    await user.click(
      screen.getByRole('option', {
        name: 'Condition error',
      })
    );
    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeEnabled();

    expect(
      screen.getByRole('option', {
        name: 'Condition error',
        selected: true,
      })
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Reset Filters'}));

    expect(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();
  });
});
