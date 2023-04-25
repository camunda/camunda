/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {BPMNDiagram, Table} from 'components';

import DurationHeatmapModal from './DurationHeatmapModal';

console.error = jest.fn();

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
    importXML = jest.fn();
    get = () => {
      return this.elementRegistry;
    };
  };
});

const validProps = {
  report: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        properties: ['duration'],
      },
      groupBy: {
        type: 'flowNodes',
      },
      visualization: 'heat',
      configuration: {
        heatmapTargetValue: {
          active: false,
          values: {
            a: {
              value: 12,
              unit: 'days',
            },
          },
        },
      },
    },
    result: {data: []},
  },
};

it('should display the bpmn diagram in the modal', () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  expect(node.find(BPMNDiagram)).toExist();
});

it('should display a list of flow nodes in a table', async () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  node.setProps({open: true});
  await flushPromises();

  const body = node.find(Table).prop('body');

  expect(body[0][0]).toBe('Element A');
  expect(body[1][0]).toBe('Element B');
  expect(body[2][0]).toBe('Element C');
});

it('should save the changes target values', async () => {
  const spy = jest.fn();

  const node = shallow(<DurationHeatmapModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')('34');
  node.instance().setTarget('unit', 'a')('years');

  spy.mockClear();

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    a: {
      unit: 'years',
      value: 34,
    },
  });
});

it('should apply previously defined target values to input fields', async () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  node.setProps({open: true});
  await flushPromises();

  expect(node.state('values').a.value).toBe('12');
  expect(node.state('values').a.unit).toBe('days');
});

it('should set isInvalid property for input if value is invalid', async () => {
  global.ResizeObserver = class {
    observe(element, initObject) {}
  };
  const node = mount(<DurationHeatmapModal {...validProps} />);
  node.setProps({open: true});
  await flushPromises();

  node.instance().setTarget('value', 'a')('invalid');

  await node.update();

  expect(node.find('.selection > [type="number"]').first().props()).toHaveProperty(
    'isInvalid',
    true
  );
});
