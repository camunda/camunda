/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsBanner} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {mockIncidents} from 'modules/mocks/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {Paths} from 'modules/Routes';

const mockProps = {
  onClick: jest.fn(),
  isArrowFlipped: false,
  isOpen: false,
};

type Props = {
  children?: React.ReactNode;
};

const {fetchIncidents} = incidentsStore;

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
      </Routes>
    </MemoryRouter>
  );
};

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', async () => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    await fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show the right text for more than 1 incident', async () => {
    mockFetchProcessInstanceIncidents().withSuccess({
      ...mockIncidents,
      count: 2,
    });

    await fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('2 Incidents occurred')).toBeInTheDocument();
  });
});
