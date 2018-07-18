import React from 'react';
import {shallow} from 'enzyme';
import {mockResolvedAsyncFn} from 'modules/testUtils';

import SelectionList from './SelectionList';
import * as selectionsApi from 'modules/api/selections/selections';
import * as instancesApi from 'modules/api/instances/instances';

const demoInstance = {
  id: '4294984040',
  workflowId: '1',
  startDate: '2018-07-10T08:58:58.073+0000',
  endDate: null,
  state: 'ACTIVE',
  businessKey: 'demoProcess',
  incidents: [
    {
      id: '4295665536',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage: 'No data found for query $.foo.',
      state: 'ACTIVE',
      activityId: 'taskA',
      activityInstanceId: '4294984912',
      jobId: null
    }
  ],
  activities: []
};

const times = x => f => {
  if (x > 0) {
    f();
    times(x - 1)(f);
  }
};

const workfowInstances = [];
times(10)(() => workfowInstances.push(demoInstance));

const mockSelectionData = {workfowInstances, totalCount: 145};

selectionsApi.batchRetry = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstanceBySelection = mockResolvedAsyncFn(
  mockSelectionData
);

describe.only('SelectionList', () => {
  let node;
  let selections;

  beforeEach(async () => {
    selections = [
      {
        exclusionList: [],
        query: {active: true, incidents: true, ids: Array(0)}
      }
    ];
    node = shallow(<SelectionList selections={selections} />);
    await node.instance().componentDidMount();
  });

  it('should fetch the instances and count for each selection', async () => {
    expect(instancesApi.fetchWorkflowInstanceBySelection).toHaveBeenCalled();
  });

  it('should call the retrySelection with the selection query', () => {
    node.instance().retrySelection();
    expect(selectionsApi.batchRetry).toHaveBeenCalled();
  });

  it('should call deleteSelection with selectionId', () => {
    // given
    expect(node.instance().state.selectionsInstances.length).toBe(20);

    // when
    node.instance().deleteSelection(0);

    // then
    expect(node.instance().state.selectionsInstances.length).toBe(19);
  });
});
