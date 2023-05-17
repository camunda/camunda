/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsTable} from '../index';
import {createIncident} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {Wrapper, incidentsMock, firstIncident} from './mocks';

describe('Selection', () => {
  it('should deselect selected incident', async () => {
    incidentsStore.setIncidents({
      ...incidentsMock,
      incidents: [firstIncident],
      count: 1,
    });
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: firstIncident.flowNodeId,
      isMultiInstance: false,
    });

    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
    expect(screen.getByRole('row', {selected: true})).toBeInTheDocument();

    await user.click(screen.getByRole('row', {selected: true}));
    expect(screen.getByRole('row', {selected: false})).toBeInTheDocument();
  });

  it('should select single incident when multiple incidents are selected', async () => {
    const incidents = [
      createIncident({flowNodeId: 'myTask'}),
      createIncident({flowNodeId: 'myTask'}),
    ];

    incidentsStore.setIncidents({...incidentsMock, incidents});
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'myTask',
      isMultiInstance: false,
    });

    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
    expect(screen.getAllByRole('row', {selected: true})).toHaveLength(2);

    const [firstRow] = screen.getAllByRole('row', {
      name: /condition error/i,
    });

    expect(firstRow).toBeInTheDocument();
    await user.click(firstRow!);

    expect(
      screen.getByRole('row', {
        name: /condition error/i,
        selected: true,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: /condition error/i,
        selected: false,
      })
    ).toBeInTheDocument();
  });
});
