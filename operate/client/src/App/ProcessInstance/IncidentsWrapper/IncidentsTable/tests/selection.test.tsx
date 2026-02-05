/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '..';
import {createProcessInstance} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {Wrapper, firstIncident, secondIncident} from './mocks';
import * as selectionUtils from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';

// TanStack query is still fetching the data when the test executes, so "useRootNode"
// falls back to undefined for the flowNodeInstanceId.
const rootNode = {flowNodeInstanceId: undefined, isMultiInstance: false};

describe.skip('Selection', () => {
  beforeEach(() => {
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );
    mockFetchElementInstance('18239123812938').withSuccess({
      elementInstanceKey: '18239123812938',
      elementId: 'StartEvent_1',
      elementName: 'Start Event',
      type: 'START_EVENT',
      state: 'COMPLETED',
      startDate: '2020-08-18T12:07:33.953+0000',
      endDate: '2020-08-18T12:07:34.034+0000',
      processDefinitionId: 'calledInstance',
      processInstanceKey: '1',
      processDefinitionKey: '123',
      hasIncident: true,
      tenantId: '<default>',
    });
  });

  it('should deselect selected incident', async () => {
    const spy = vi.spyOn(selectionUtils, 'clearSelection');

    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={[{...firstIncident, isSelected: true}]}
      />,
      {wrapper: Wrapper},
    );
    expect(screen.getByRole('row', {selected: true})).toBeInTheDocument();

    await user.click(screen.getByRole('row', {selected: true}));

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(rootNode);
  });

  it('should select single incident when multiple incidents are selected', async () => {
    const spy = vi.spyOn(selectionUtils, 'selectFlowNode');

    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={[
          {...firstIncident, isSelected: true, errorType: 'CONDITION_ERROR'},
          {...secondIncident, isSelected: true, errorType: 'CONDITION_ERROR'},
        ]}
      />,
      {wrapper: Wrapper},
    );
    expect(screen.getAllByRole('row', {selected: true})).toHaveLength(2);

    const [firstRow] = screen.getAllByRole('row', {
      name: /condition error/i,
    });

    expect(firstRow).toBeInTheDocument();
    await user.click(firstRow);

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(rootNode, {
      flowNodeId: firstIncident.elementId,
      flowNodeInstanceId: firstIncident.elementInstanceKey,
      isMultiInstance: false,
    });
  });
});
