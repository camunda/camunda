/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';

const PROCESS_INSTANCE_ID = '2251799813689404';

describe('stores/flowNodeSelection', () => {
  beforeAll(async () => {
    mockServer.use(
      rest.get(`/api/process-instances/:id`, (_, res, ctx) =>
        res.once(
          ctx.json({
            id: PROCESS_INSTANCE_ID,
            state: 'ACTIVE',
          })
        )
      )
    );

    await processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
  });

  afterAll(() => {
    processInstanceDetailsStore.reset();
  });

  beforeEach(() => {
    flowNodeSelectionStore.init();
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
  });

  it('should initially select process instance', () => {
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: undefined,
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });

  it('should select flow node', () => {
    const selection = {flowNodeId: 'startEvent', isMultiInstance: false};
    const unselectedInstance = {flowNodeId: 'endEvent'};

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(true);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should select flow node instance', () => {
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
      isMultiInstance: false,
    };
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
      isMultiInstance: false,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should unselect and fallback to process instance', () => {
    const selection = {flowNodeId: undefined};
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });

  it('should select multi instance flow node', () => {
    const selection = {
      flowNodeId: 'subProcess',
      isMultiInstance: true,
    };

    const unselectedInstance = {
      flowNodeId: 'subProcess',
      isMultiInstance: false,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should select non-multi instance flow node', () => {
    const selection = {
      flowNodeId: 'subProcess',
      isMultiInstance: false,
    };

    const unselectedInstance = {
      flowNodeId: 'subProcess',
      isMultiInstance: true,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should fallback to process instance when selecting twice', () => {
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
    };
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });
});
