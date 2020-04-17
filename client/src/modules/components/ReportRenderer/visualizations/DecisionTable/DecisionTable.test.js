/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {DecisionTable} from './DecisionTable';
import Viewer from 'dmn-js';

import {shallow} from 'enzyme';

jest.mock('dmn-js', () =>
  jest.fn().mockImplementation(() => ({
    importXML: jest.fn().mockImplementation((xml, callback) => callback()),
    open: jest.fn(),
    getViews: jest.fn().mockReturnValue([
      {type: 'decisionTable', element: {id: 'a'}},
      {type: 'decisionTable', element: {id: 'key'}},
      {type: 'decisionTable', element: {id: 'c'}},
    ]),
  }))
);

const flushPromises = () => new Promise((resolve) => setImmediate(resolve));

jest.mock('@bpmn-io/dmn-migrate', () => ({migrateDiagram: (xml) => xml}));

const props = {
  report: {
    data: {
      configuration: {
        xml: 'dmn xml string',
      },
      decisionDefinitionKey: 'key',
    },
    result: {
      instanceCount: 3,
      data: [
        {key: 'a', value: 1},
        {key: 'b', value: 2},
      ],
    },
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should construct a new Viewer instance', () => {
  shallow(<DecisionTable {...props} />);

  expect(Viewer).toHaveBeenCalled();
});

it('should import the provided xml', async () => {
  const node = shallow(<DecisionTable {...props} />);
  await flushPromises();

  expect(node.instance().viewer.importXML).toHaveBeenCalled();
  expect(node.instance().viewer.importXML.mock.calls[0][0]).toBe('dmn xml string');
});

it('should open the view of the appropriate decision table', async () => {
  const node = shallow(<DecisionTable {...props} />);
  await flushPromises();

  expect(node.instance().viewer.open).toHaveBeenCalled();
  expect(node.instance().viewer.open.mock.calls[0][0]).toEqual({
    type: 'decisionTable',
    element: {id: 'key'},
  });
});

it('should render content in DmnJsPortals', () => {
  const node = shallow(<DecisionTable {...props} />);

  node.setState({
    entryPoints: {
      rules: {
        a: document.createElement('td'),
        b: document.createElement('td'),
      },
      summary: document.createElement('td'),
    },
  });

  expect(node).toMatchSnapshot();
});

it('should display meaningful data if there are no evaluations', () => {
  const node = shallow(
    <DecisionTable
      {...props}
      report={{data: props.report.data, result: {instanceCount: 0, data: []}}}
    />
  );

  node.setState({
    entryPoints: {
      rules: {
        a: document.createElement('td'),
        b: document.createElement('td'),
      },
      summary: document.createElement('td'),
    },
  });

  expect(node).toMatchSnapshot();
});
