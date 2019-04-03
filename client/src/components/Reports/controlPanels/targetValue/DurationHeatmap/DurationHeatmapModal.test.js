/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
        {id: 'c', name: 'Element C'}
      ];

      this.elementRegistry = {
        filter: () => {
          return {
            map: () => this.elements
          };
        }
      };
    }
    attachTo = jest.fn();
    importXML = jest.fn((xml, cb) => cb());
    get = () => {
      return this.elementRegistry;
    };
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    formatters: {
      duration: a => a
    },
    numberParser: {
      isValidNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value),
      isPositiveNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value) && +value > 0,
      isIntegerNumber: value => /^[+-]?\d+?$/.test(value),
      isFloatNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value)
    }
  };
});

const validProps = {
  report: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        operation: 'avg',
        property: 'duration'
      },
      groupBy: {
        type: 'flowNodes'
      },
      visualization: 'heat',
      configuration: {
        heatmapTargetValue: {
          active: false,
          values: {
            a: {
              value: 12,
              unit: 'days'
            }
          }
        }
      }
    },
    result: {}
  }
};

it('should display the bpmn diagram in the modal', () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  expect(node.find(BPMNDiagram)).toBePresent();
});

it('should display a list of flow nodes in a table', async () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  await node.setProps({open: true});

  const body = node.find(Table).prop('body');

  expect(body[0][0]).toBe('Element A');
  expect(body[1][0]).toBe('Element B');
  expect(body[2][0]).toBe('Element C');
});

it('should save the changes target values', async () => {
  const spy = jest.fn();

  const node = shallow(<DurationHeatmapModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')({target: {value: '34'}});
  node.instance().setTarget('unit', 'a')({target: {value: 'years'}});

  spy.mockClear();

  node.find('[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    a: {
      unit: 'years',
      value: 34
    }
  });
});

it('should apply previously defined target values to input fields', async () => {
  const node = shallow(<DurationHeatmapModal {...validProps} />);

  await node.setProps({open: true});

  expect(node.state('values').a.value).toBe('12');
  expect(node.state('values').a.unit).toBe('days');
});

it('should set isInvalid property for input if value is invalid', async () => {
  const node = mount(<DurationHeatmapModal {...validProps} />);
  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')({target: {value: 'invalid'}});

  await node.update();

  expect(
    node
      .find('.DurationHeatmapModal__selection--input')
      .first()
      .props()
  ).toHaveProperty('isInvalid', true);
});
