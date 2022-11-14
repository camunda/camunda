/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsBanner} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {mockIncidents} from 'modules/mocks/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';

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
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
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
