import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {STATE} from 'modules/constants/instance';
import Copyright from 'modules/components/Copyright';

import Instance from './Instance';
import Header from './../Header';
import DiagramPanel from './DiagramPanel';
import InstanceDetail from './InstanceDetail';
import * as api from 'modules/api/instances/instances';

const xmlMock = '<foo />';
api.workflowXML = mockResolvedAsyncFn(xmlMock);
jest.mock('./DiagramPanel');

const INSTANCE = {
  id: '4294980768',
  workflowId: '1',
  startDate: '2018-06-18T08:44:52.240+0000',
  endDate: null,
  state: STATE.ACTIVE,
  businessKey: 'demoProcess',
  incidents: [
    {
      id: '4295763008',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: STATE.ACTIVE,
      activityId: 'taskA',
      activityInstanceId: '4294983744',
      taskId: null
    }
  ]
};

// mock api
api.fetchWorkflowInstance = mockResolvedAsyncFn(INSTANCE);

const initialState = {
  instance: null,
  loaded: false
};

const component = (
  <Instance
    match={{params: {id: INSTANCE.id}, isExact: true, path: '', url: ''}}
  />
);
describe('Instance', () => {
  beforeEach(() => {
    api.fetchWorkflowInstance.mockClear();
  });

  describe('initial state', () => {
    it('should render initially with no data', () => {
      const node = shallow(component);
      expect(node.state()).toEqual(initialState);
      expect(node.text()).toContain('Loading');
    });
  });

  describe('data fetching', () => {
    it('should fetch instance information', async () => {
      // given
      shallow(component);

      // then fetching is done with the right id
      expect(api.fetchWorkflowInstance).toHaveBeenCalledTimes(1);
      expect(api.fetchWorkflowInstance.mock.calls[0][0]).toEqual(INSTANCE.id);
    });

    it('should change state after data fetching', async () => {
      // given
      const node = shallow(component);

      // when data fetched
      await flushPromises();
      node.update();

      // then
      expect(node.state('instance')).toEqual(INSTANCE);
      expect(node.state('loaded')).toBe(true);
    });
  });

  describe('rendering', () => {
    it('should display a Header, DiagramPanel and Copyright', async () => {
      // given
      const node = shallow(component);
      await flushPromises();
      node.update();

      // then
      expect(node.find(Header)).toHaveLength(1);
      expect(node.find(DiagramPanel)).toHaveLength(1);
      expect(node.find(Copyright)).toHaveLength(1);
      const Detail = node.find(Header).prop('detail');
      expect(Detail.type).toBe(InstanceDetail);
      expect(Detail.props.instance).toEqual(INSTANCE);
    });
  });
});
