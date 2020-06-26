/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {BPMNDiagram} from 'components';

import NodeDuration from './NodeDuration';

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  return class Viewer {
    constructor() {
      this.elements = [
        {id: 'a', name: 'Element A'},
        {id: 'b', name: 'Element B'},
        {id: 'c', name: 'Element C'},
      ];

      this.elementRegistry = {
        filter: () => {
          return {
            map: () => this.elements,
          };
        },
      };
    }
    attachTo = jest.fn();
    importXML = jest.fn((xml, cb) => cb());
    get = () => {
      return this.elementRegistry;
    };
  };
});

const props = {
  close: jest.fn(),
  addFilter: jest.fn(),
  xml: 'testXML',
  data: [],
};

const flushPromises = () => new Promise((resolve) => setImmediate(resolve));

it('should display the bpmn diagram in the modal', () => {
  const node = shallow(<NodeDuration {...props} />);

  expect(node.find(BPMNDiagram)).toExist();
});

it('should add duration filters correctly', async () => {
  const spy = jest.fn();

  const node = shallow(<NodeDuration {...props} addFilter={spy} />);

  await flushPromises();

  node.find('NodesTable').prop('onChange')({a: {unit: 'years', value: '12', operator: '>'}});

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    data: {a: {operator: '>', unit: 'years', value: 12}},
    type: 'flowNodeDuration',
  });
});

it('should apply previously defined values', async () => {
  const node = shallow(
    <NodeDuration
      {...props}
      filterData={{
        type: 'flowNodeDuration',
        data: {a: {operator: '>', unit: 'years', value: 12}},
      }}
    />
  );

  node.setProps({open: true});
  await flushPromises();

  expect(node.state('values').a.value).toBe('12');
  expect(node.state('values').a.unit).toBe('years');
});
