import React from 'react';
import {shallow} from 'enzyme';

import {
  mockResolvedAsyncFn,
  createInstance,
  createOperation
} from 'modules/testUtils';

import {
  INSTANCE_STATE,
  OPERATION_STATE,
  OPERATION_TYPE
} from 'modules/constants';

import Actions from './Actions';
import ActionStatus from './ActionStatus';
import ActionItems from './ActionItems';

import * as Styled from './styled';
import * as service from './service';
import * as api from 'modules/api/instances/instances';

// mocking api
api.applyOperation = mockResolvedAsyncFn();

// mocking services
let isWithIncident;
let isRunning;

service.isWithIncident = jest.fn(() => isWithIncident);
service.isRunning = jest.fn(() => isRunning);
service.wrapIdinQuery = jest.fn();

describe('Actions', () => {
  let node;
  let mockOperation, mockInstance;

  it('should match snapshots', () => {
    // when
    mockOperation = createOperation({state: OPERATION_STATE.SCHEDULED});
    mockInstance = createInstance({operations: [mockOperation]});
    node = shallow(<Actions instance={mockInstance} />);
    //then
    expect(node).toMatchSnapshot();

    // when
    mockOperation = createOperation({state: OPERATION_STATE.FAILED});
    mockInstance = createInstance({operations: [mockOperation]});
    node = shallow(<Actions instance={mockInstance} />);

    // then
    expect(node).toMatchSnapshot();
  });

  it('should pass props to the status component', () => {
    // when
    mockOperation = createOperation({
      state: OPERATION_STATE.SCHEDULED,
      type: OPERATION_TYPE.CANCEL
    });
    mockInstance = createInstance({operations: [mockOperation]});
    node = shallow(<Actions instance={mockInstance} />);

    //then
    expect(node.find(ActionStatus).props().operationState).toBe(
      OPERATION_STATE.SCHEDULED
    );

    expect(node.find(ActionStatus).props().operationType).toBe(
      OPERATION_TYPE.CANCEL
    );
  });

  describe('Action Buttons', () => {
    it('should render action buttons ', () => {
      // when
      mockInstance = createInstance({operations: []});

      node = shallow(<Actions instance={mockInstance} />);
      // then
      expect(node.find(ActionItems)).toExist();
    });

    describe('retry', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: INSTANCE_STATE.INCIDENT});
        isWithIncident = true;
        isRunning = false;
        node = shallow(<Actions instance={mockInstance} />);
      });

      it('should render retry action item', async () => {
        const actionType = node.find(ActionItems.Item).props().type;
        expect(actionType).toBe(OPERATION_TYPE.UPDATE_RETRIES);
      });

      it('should call the retry api onClick ', () => {
        //given
        const actionItem = node.find(ActionItems.Item);

        // when
        actionItem.simulate('click');

        // then
        expect(service.wrapIdinQuery).toBeCalledWith(
          OPERATION_TYPE.UPDATE_RETRIES,
          mockInstance
        );
        expect(api.applyOperation).toBeCalledWith(
          OPERATION_TYPE.UPDATE_RETRIES,
          service.wrapIdinQuery()
        );
      });
    });
    describe('cancel', () => {
      beforeEach(() => {
        mockInstance = createInstance({state: INSTANCE_STATE.ACTIVE});
        isWithIncident = false;
        isRunning = true;
        node = shallow(<Actions instance={mockInstance} />);
      });
      it('should render cancel action item', async () => {
        const actionType = node.find(ActionItems.Item).props().type;
        expect(actionType).toBe(OPERATION_TYPE.CANCEL);
      });
      it('should call the cancel api onClick ', () => {
        //given
        const actionItem = node.find(ActionItems.Item);
        // when
        actionItem.simulate('click');
        // then
        expect(service.wrapIdinQuery).toBeCalledWith(
          OPERATION_TYPE.CANCEL,
          mockInstance
        );
        expect(api.applyOperation).toBeCalledWith(
          OPERATION_TYPE.CANCEL,
          service.wrapIdinQuery()
        );
      });
    });
  });
});
