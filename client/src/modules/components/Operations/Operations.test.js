/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {createInstance, createOperation} from 'modules/testUtils';

import {STATE, OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import Operations from './Operations';
import OperationStatus from 'modules/components/OperationStatus';
import OperationItems from './OperationItems';

jest.mock('modules/utils/bpmn');

describe('Operations', () => {
  let mockOperation, mockInstance, onButtonClick;

  it('should match snapshots', () => {
    // when
    mockOperation = createOperation({state: OPERATION_STATE.SCHEDULED});
    mockInstance = createInstance({operations: [mockOperation]});
    let node = shallow(<Operations.WrappedComponent instance={mockInstance} />);
    //then
    expect(node).toMatchSnapshot();
  });

  it('should pass props OperationStatus', () => {
    // when
    mockOperation = createOperation({
      state: OPERATION_STATE.SCHEDULED,
      type: OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    });
    mockInstance = createInstance({operations: [mockOperation]});
    const mockProps = {
      forceSpinner: true,
      onButtonClick: jest.fn()
    };

    const node = shallow(
      <Operations.WrappedComponent instance={mockInstance} {...mockProps} />
    );

    //then
    expect(node.find(OperationStatus).props().operationState).toBe(
      OPERATION_STATE.SCHEDULED
    );

    expect(node.find(OperationStatus).props().operationType).toBe(
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    );
    expect(node.find(OperationStatus).props().forceSpinner).toEqual(
      mockProps.forceSpinner
    );
  });

  describe('Operation Buttons', () => {
    it('should render operation buttons for active instance', () => {
      // when
      mockInstance = createInstance({state: STATE.ACTIVE, operations: []});
      const node = shallow(
        <Operations.WrappedComponent instance={mockInstance} />
      );
      const OperationItemsNode = node.find(OperationItems);
      const Button = OperationItemsNode.find(OperationItems.Item);

      // then
      expect(OperationItemsNode).toExist();
      expect(Button.length).toEqual(1);
      expect(Button.props().type).toEqual(
        OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
      );
    });

    it('should render operation buttons for instance with incidents', () => {
      // when
      mockInstance = createInstance({state: STATE.INCIDENT, operations: []});
      const node = shallow(
        <Operations.WrappedComponent instance={mockInstance} />
      );
      const OperationItemsNode = node.find(OperationItems);

      // then
      expect(OperationItemsNode).toExist();
      expect(OperationItemsNode.find(OperationItems.Item).length).toEqual(2);
      expect(
        OperationItemsNode.find(OperationItems.Item)
          .at(0)
          .props().type
      ).toEqual(OPERATION_TYPE.RESOLVE_INCIDENT);
      expect(
        OperationItemsNode.find(OperationItems.Item)
          .at(1)
          .props().type
      ).toEqual(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE);
    });

    describe('Retry', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: STATE.INCIDENT});
        onButtonClick = jest.fn();
      });
      afterEach(() => {
        jest.clearAllMocks();
      });

      it('should handle retry of instance incident ', async () => {
        //given
        const node = shallow(
          <Operations.WrappedComponent
            instance={mockInstance}
            dataManager={createMockDataManager()}
            onButtonClick={onButtonClick}
          />
        );

        const operationItem = node.find(OperationItems.Item).at(0);

        // when
        operationItem.simulate('click');

        const {dataManager} = node.instance().props;

        expect(dataManager.applyOperation).toBeCalledWith(mockInstance.id, {
          operationType: OPERATION_TYPE.RESOLVE_INCIDENT
        });
        // expect Spinner to appear
        expect(node.find(OperationStatus).props().operationState).toEqual(
          OPERATION_STATE.SCHEDULED
        );

        // expect callback to be called
        expect(onButtonClick).toHaveBeenCalled();
      });
    });

    describe('Cancel', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: STATE.ACTIVE});
        onButtonClick = jest.fn();
      });
      afterEach(() => {
        jest.clearAllMocks();
      });

      it('should handle the cancelation of an instance ', async () => {
        //given
        const node = shallow(
          <Operations.WrappedComponent
            instance={mockInstance}
            dataManager={createMockDataManager()}
            onButtonClick={onButtonClick}
          />
        );
        const operationItem = node.find(OperationItems.Item);

        // when
        operationItem.simulate('click');

        const {dataManager} = node.instance().props;

        // then
        expect(dataManager.applyOperation).toBeCalledWith(mockInstance.id, {
          operationType: OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
        });

        // expect Spinner to appear
        expect(node.find(OperationStatus).props().operationState).toEqual(
          OPERATION_STATE.SCHEDULED
        );

        // expect callback to be called
        expect(onButtonClick).toHaveBeenCalled();
      });
    });
  });
});
