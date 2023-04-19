/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsFilter} from './index';
import {render, screen} from 'modules/testing-library';

import {mockIncidents, mockIncidentsWithManyErrors} from './index.setup';
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
  it('should render pills by incident type', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Condition error 2'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Extract value error 1'})
    ).toBeInTheDocument();
  });

  it('should render pills by flow node', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'flowNodeId_exclusiveGateway 1'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'flowNodeId_alwaysFailingTask 2'})
    ).toBeInTheDocument();
  });

  it('should show a more button', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(
      mockIncidentsWithManyErrors
    );

    await fetchIncidents('1');

    const {user} = render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });
    expect(
      screen.queryByRole('button', {name: 'error type 6 1'})
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^1 more/}));

    expect(
      screen.getByRole('button', {name: 'error type 6 1'})
    ).toBeInTheDocument();
  });

  it('should disable/enable clear all button depending on selected pills', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    const {user} = render(<IncidentsFilter />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByRole('button', {
        name: 'Condition error 2',
        pressed: false,
      })
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Clear All'})).toBeDisabled();

    await user.click(screen.getByRole('button', {name: 'Condition error 2'}));

    expect(
      screen.getByRole('button', {
        name: 'Condition error 2',
        pressed: true,
      })
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Clear All'})).toBeEnabled();

    await user.click(screen.getByRole('button', {name: 'Clear All'}));

    expect(
      screen.getByRole('button', {
        name: 'Condition error 2',
        pressed: false,
      })
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Clear All'})).toBeDisabled();
  });
});
