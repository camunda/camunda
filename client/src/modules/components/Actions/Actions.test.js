import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import {INSTANCE_STATE, OPERATION_TYPE} from 'modules/constants';

import Actions from './Actions';
import ActionItems from './ActionItems';

import * as service from './service';
import * as api from 'modules/api/instances/instances';

const mockInstance = {
  activities: [],
  businessKey: 'orderProcess',
  endDate: null,
  id: '8590375632-2',
  incidents: [],
  operations: [],
  sequenceFlows: [],
  startDate: '2018-09-11T07:10:30.834+0000',
  state: 'ACTIVE',
  workflowId: '5'
};

// mocking api
api.applyOperation = mockResolvedAsyncFn();

// mocking services
let isWithIncident;
let isRunning;
let query;

service.isWithIncident = jest.fn(() => isWithIncident);
service.isRunning = jest.fn(() => isRunning);
service.wrapIdinQuery = jest.fn();

describe('Actions', () => {
  let node;

  beforeEach(() => {
    isRunning = false;
    isWithIncident = false;
  });

  it('should only render the actionItems component', () => {
    // when
    node = shallow(<Actions instance={mockInstance} />);

    // then
    expect(node.find(ActionItems)).toExist();
    expect(node.children().length).toBe(0);
  });

  describe('retry', () => {
    beforeEach(() => {
      isWithIncident = true;
      node = shallow(<Actions instance={mockInstance} />);
    });

    it('should render retry action item', async () => {
      expect(node.children().length).toBe(1);
      const actionType = node.props().children[0].props.type;
      expect(actionType).toBe(OPERATION_TYPE.UPDATE_RETRIES);
    });

    it('should call the retry api onClick ', () => {
      const actionItem = node.find(ActionItems.Item);
      actionItem.simulate('click');
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
      isRunning = true;
      node = shallow(<Actions instance={mockInstance} />);
    });

    it('should render cancel action item', async () => {
      expect(node.children().length).toBe(1);
      const actionType = node.props().children[1].props.type;
      expect(actionType).toBe(OPERATION_TYPE.CANCEL);
    });

    it('should call the cancel api onClick ', () => {
      const actionItem = node.find(ActionItems.Item);
      actionItem.simulate('click');
      expect(service.wrapIdinQuery).toBeCalledWith(
        OPERATION_TYPE.UPDATE_RETRIES,
        mockInstance
      );
      expect(api.applyOperation).toBeCalledWith(
        OPERATION_TYPE.CANCEL,
        service.wrapIdinQuery()
      );
    });
  });
});
