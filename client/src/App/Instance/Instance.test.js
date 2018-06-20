import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn} from 'modules/testUtils';
import Copyright from 'modules/components/Copyright';

import Instance from './Instance';
import Header from './../Header';
import DiagramPanel from './DiagramPanel';
import InstanceDetail from './InstanceDetail';
import * as api from './api';

const xmlMock = '<foo />';
api.workflowXML = mockResolvedAsyncFn(xmlMock);
jest.mock('./DiagramPanel');

const INSTANCE = {
  id: '4294980768',
  workflowId: '1',
  startDate: '2018-06-18T08:44:52.240+0000',
  endDate: null,
  state: 'ACTIVE',
  businessKey: 'demoProcess',
  incidents: [
    {
      id: '4295763008',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: 'ACTIVE',
      activityId: 'taskA',
      activityInstanceId: '4294983744',
      taskId: null
    }
  ]
};

// mock api
api.workflowInstance = mockResolvedAsyncFn(INSTANCE);

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
  // data handling
  it('should fetch instance information', async () => {
    const node = shallow(component);

    const spyFetch = jest.spyOn(node.instance(), 'fetchWorkflowInstance');
    await node.instance().componentDidMount();

    expect(spyFetch).toHaveBeenCalled();
    // fetching is done with the right id
    expect(spyFetch.mock.calls[0][0]).toEqual(INSTANCE.id);
  });

  it.skip('should change state after data fetching', async () => {
    const node = shallow(component);
    // fetch data
    await node.instance().componentDidMount();
    node.update();

    expect(node.state('instance')).toEqual(INSTANCE);
    expect(node.state('loaded')).toBe(true);
  });

  // initial state
  it('should render initially with no data', () => {
    const node = shallow(component);
    expect(node.state()).toEqual(initialState);
  });

  it('should display a Loading message before fetching data', () => {
    const node = shallow(component);
    expect(node.text()).toContain('Loading');
  });

  // rendering
  it('should display a Header, DiagramPanel and Copyright', async () => {
    const node = shallow(component);
    // fetch data
    await node.instance().componentDidMount();
    node.update();

    expect(node.find(Header)).toHaveLength(1);
    expect(node.find(DiagramPanel)).toHaveLength(1);
    expect(node.find(Copyright)).toHaveLength(1);
  });

  it('should render InstanceDetail in the Header as a detail', async () => {
    const node = shallow(component);
    // fetch data
    await node.instance().componentDidMount();
    node.update();

    const Detail = node.find(Header).prop('detail');
    expect(Detail.type).toBe(InstanceDetail);
  });

  it('should InstanceDetail in the Header with the instance id', async () => {
    const node = shallow(component);
    // fetch data
    await node.instance().componentDidMount();
    node.update();

    const Detail = node.find(Header).prop('detail');
    expect(Detail.props.instanceId).toEqual(INSTANCE.id);
  });
});
