import React from 'react';
import {mount} from 'enzyme';

import {loadCorrelationData} from './service';

import ChartRenderer from 'chart.js';

import Statistics from './Statistics';

jest.mock('./service', () => {
  return {
    loadCorrelationData: jest.fn().mockReturnValue({
      total: 10,
      followingNodes: {
        a: {activitiesReached: 3, activityCount: 1},
        b: {activitiesReached: 2, activityCount: 2}
      }
    })
  };
});

jest.mock('services', () => {
  return {
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar'
    })
  };
});

jest.mock('chart.js', () =>
  jest.fn().mockImplementation(() => {
    return {destroy: jest.fn()};
  })
);

const props = {
  config: {
    filter: []
  },
  gateway: {id: 'g', outgoing: [{targetRef: {id: 'a'}}, {targetRef: {id: 'b'}}]},
  endEvent: {id: 'e'}
};

it('should load correlation data initially', () => {
  mount(<Statistics {...props} />);

  expect(loadCorrelationData).toHaveBeenCalled();
});

it('should load updated correlation when selection or configuration changes', () => {
  const node = mount(<Statistics {...props} />);

  loadCorrelationData.mockClear();
  node.setProps({gateway: {id: 'g2', outgoing: props.gateway.outgoing}});

  expect(loadCorrelationData).toHaveBeenCalled();
  expect(loadCorrelationData.mock.calls[0][3]).toBe('g2');

  loadCorrelationData.mockClear();
  node.setProps({...props, config: {filter: ['aFilter']}});

  expect(loadCorrelationData).toHaveBeenCalled();
  expect(loadCorrelationData.mock.calls[0][2]).toEqual(['aFilter']);
});

it('should create two Charts', async () => {
  ChartRenderer.mockClear();

  const node = mount(<Statistics {...props} />);

  node.setState({
    data: {
      total: 10,
      followingNodes: {
        a: {activitiesReached: 3, activityCount: 1},
        b: {activitiesReached: 2, activityCount: 2}
      }
    }
  });

  expect(ChartRenderer.mock.instances.length).toBe(2);
});
