/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getDiagramElementsBetween} from 'services';

import {loadCorrelationData} from './service';

import Statistics from './Statistics';

jest.mock('components', () => {
  return {
    LoadingIndicator: () => <span>loading</span>,
  };
});

jest.mock('./service', () => {
  return {
    loadCorrelationData: jest.fn().mockReturnValue({
      total: 10,
      followingNodes: {
        a: {activitiesReached: 3, activityCount: 1},
        b: {activitiesReached: 2, activityCount: 2},
      },
    }),
  };
});

jest.mock('services', () => {
  return {
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar',
    }),
    getDiagramElementsBetween: jest.fn().mockReturnValue([]),
  };
});

const initialProps = {
  config: {
    filter: [],
    processDefinitionKey: null,
    tenantIds: [],
    processDefinitionVersions: null,
  },
  gateway: null,
  endEvent: null,
};

const props = {
  config: {
    filter: [],
    processDefinitionKey: 'a',
    tenantIds: [null],
    processDefinitionVersions: ['1'],
  },
  gateway: {
    id: 'g',
    outgoing: [{name: 'testLabel', targetRef: {id: 'a', name: 'a'}}, {targetRef: {id: 'b'}}],
  },
  endEvent: {id: 'e'},
};

it('should show a load correlation data initially', async () => {
  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  await flushPromises();

  expect(loadCorrelationData).toHaveBeenCalled();
});

it('should load updated correlation when selection or configuration changes', async () => {
  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  await flushPromises();

  loadCorrelationData.mockClear();
  node.setProps({gateway: {id: 'g2', outgoing: props.gateway.outgoing}});

  await flushPromises();

  expect(loadCorrelationData).toHaveBeenCalled();
  expect(loadCorrelationData.mock.calls[0][4]).toBe('g2');

  loadCorrelationData.mockClear();
  node.setProps({config: {...props.config, filter: ['aFilter']}});

  await flushPromises();

  expect(loadCorrelationData).toHaveBeenCalled();
  expect(loadCorrelationData.mock.calls[0][3]).toEqual(['aFilter']);
});

it('should create two Charts', async () => {
  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  await flushPromises();

  node.setState({
    data: {
      total: 10,
      followingNodes: {
        a: {activitiesReached: 3, activityCount: 1},
        b: {activitiesReached: 2, activityCount: 2},
      },
    },
  });

  expect(node).toMatchSnapshot();
});

it('should invoke add Marker when called Mark Sequence flow function', async () => {
  getDiagramElementsBetween.mockReturnValueOnce(['elementA', 'elementB']);

  const canvas = {
    addMarker: jest.fn(),
  };

  const activeElements = [
    {
      _model: {label: 'testLabel'},
    },
  ];

  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  await flushPromises();

  node.instance().markSequenceFlow(null, canvas, activeElements, 'testMark');
  expect(canvas.addMarker).toBeCalledWith(props.gateway.outgoing[0], 'testMark');
  expect(canvas.addMarker).toBeCalledWith('elementA', 'testMark');
  expect(canvas.addMarker).toBeCalledWith('elementB', 'testMark');
});

it('should show a placeholder text initially', () => {
  const node = shallow(<Statistics {...initialProps} />);

  expect(node).toMatchSnapshot();
});

it('should show a loading indicator while stuff is loading', () => {
  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  expect(node).toMatchSnapshot();
});

it('should show a summary after loading is complete', async () => {
  const node = shallow(<Statistics {...initialProps} />);

  node.setProps(props);
  await flushPromises();

  node.update();

  expect(node).toMatchSnapshot();
});

it('should show a message if no instances passed the gateway', () => {
  const node = shallow(<Statistics {...props} />);

  node.setState({
    data: {
      total: 10,
      followingNodes: {
        a: {activitiesReached: 0, activityCount: 0},
        b: {activitiesReached: 0, activityCount: 0},
      },
    },
  });

  expect(node).toMatchSnapshot();
});
