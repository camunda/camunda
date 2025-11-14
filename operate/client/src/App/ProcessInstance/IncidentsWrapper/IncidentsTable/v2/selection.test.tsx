/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '.';
import {createInstance, createProcessInstance} from 'modules/testUtils';
import {render, screen} from 'modules/testing-library';
import {Wrapper, firstIncident, secondIncident} from './mocks';
import * as selectionUtils from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

// TanStack query is still fetching the data when the test executes, so "useRootNode"
// falls back to undefined for the flowNodeInstanceId.
const rootNode = {flowNodeInstanceId: undefined, isMultiInstance: false};

describe('Selection', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(createInstance());
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
      }),
    );
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
