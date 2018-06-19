import React from 'react';
import {shallow} from 'enzyme';
import * as api from './api';
import Instance from './Instance';
import {mockResolvedAsyncFn} from 'modules/testUtils';
import Header from './../Header';
import DiagramPanel from './DiagramPanel';
import Copyright from 'modules/components/Copyright';
import InstanceDetail from './InstanceDetail';

const xmlMock = '<foo />';
api.getWorkflowXML = mockResolvedAsyncFn(xmlMock);
jest.mock('./DiagramPanel');

const INSTANCE = {
  id: '4294980768',
  workflowDefinitionId: '1',
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

describe('Instance', () => {
  let node;

  beforeEach(() => {
    node = shallow(
      <Instance
        match={{params: {id: INSTANCE.id}, isExact: true, path: '', url: ''}}
      />
    );
  });

  it('should fetch instance information', async () => {
    const spyFetch = jest.spyOn(node.instance(), 'fetchWorkflowInstance');
    await node.instance().componentDidMount();
    expect(spyFetch).toHaveBeenCalled();
  });

  it('should display a Header', async () => {
    await node.instance().componentDidMount();
    node.update();
    expect(node.find(Header)).toHaveLength(1);
  });

  it('should display a DiagramPanel', async () => {
    await node.instance().componentDidMount();
    node.update();
    expect(node.find(DiagramPanel)).toHaveLength(1);
  });

  it('should display a Copyright', async () => {
    await node.instance().componentDidMount();
    node.update();
    expect(node.find(Copyright)).toHaveLength(1);
  });

  it('should render InstanceDetail in the Header as a detail', async () => {
    await node.instance().componentDidMount();
    node.update();
    const Detail = node.find(Header).prop('detail');
    expect(Detail.type).toBe(InstanceDetail);
  });

  it('should InstanceDetail in the Header with the instance id', async () => {
    await node.instance().componentDidMount();
    node.update();
    const Detail = node.find(Header).prop('detail');
    expect(Detail.props.instanceId).toEqual(INSTANCE.id);
  });
});
