/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getVariableLabel} from 'variables';

import ColumnSelection from './ColumnSelection';

jest.mock('variables', () => ({
  getVariableLabel: jest.fn(),
}));

const data = {
  configuration: {
    tableColumns: {
      includeNewVariables: true,
      includedColumns: ['processDefinitionKey', 'processInstanceId', 'flowNodeDuration:dur1'],
      excludedColumns: [],
    },
  },
};

it('should have a switch for every column', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {
          data: [
            {
              processDefinitionKey: 1,
              processInstanceId: 2,
              businessKey: 3,
              variables: {x: 1, y: 2},
              flowNodeDurations: {
                dur1: {name: 'dur1', value: null},
                dur2: {name: 'dur2', value: 1000},
              },
              counts: {
                openIncidents: 0,
              },
            },
          ],
        },
        data,
      }}
    />
  );

  expect(node.find('ColumnSwitch').length).toBe(8);
});

it('should change the switches labels to space case instead of camelCase for non variables', () => {
  getVariableLabel.mockReturnValueOnce('testVariable');
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{processDefinitionKey: 1, variables: {testVariable: 1}}]},
        data,
      }}
    />
  );

  expect(node.find('ColumnSwitch').at(0).prop('label')).toContain('Process definition key');
  expect(node.find('ColumnSwitch').at(1).prop('label')).toContain('testVariable');
});

it('should call onChange with an empty included if all columns are excluded', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{processDefinitionKey: 1, processInstanceId: 3, variables: {}}]},
        data,
      }}
      onChange={spy}
    />
  );
  node.find('AllColumnsButtons').prop('disableAll')();

  expect(spy).toHaveBeenCalledWith({
    tableColumns: {
      excludedColumns: {$set: ['processDefinitionKey', 'processInstanceId']},
      includedColumns: {$set: []},
    },
  });
});

it('should update configuration when changing include variables checkbox', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      onChange={spy}
      report={{
        data,
        result: {data: [{processDefinitionKey: 1, processInstanceId: 3, variables: {}}]},
      }}
    />
  );

  node.find('Checkbox').simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith({tableColumns: {includeNewVariables: {$set: false}}});
});

it('should not crash if the report result is empty', () => {
  shallow(
    <ColumnSelection
      report={{
        data: {configuration: {}},
        result: {data: {}},
      }}
    />
  );
});

it('should not crash if the report result is missing', () => {
  shallow(
    <ColumnSelection
      report={{
        data: {configuration: {}},
      }}
    />
  );
});

it('should resolve the label of the variable if it exists', () => {
  getVariableLabel.mockReturnValueOnce('variableLabel');
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{variables: {testVariable: 1}}]},
        data,
      }}
    />
  );

  expect(node.find('ColumnSwitch').prop('label')).toBe('variableLabel');
});

it('should resolve the label of the flow node duration if it exists', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{flowNodeDurations: {dur1: {name: 'dur1Name'}}}]},
        data,
      }}
    />
  );

  expect(node.find('ColumnSwitch').prop('label')).toBe('dur1Name');
});

it('should fallback the label of the flow node duration to key if name doesnt exist', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{flowNodeDurations: {dur1: {value: 0}}}]},
        data,
      }}
    />
  );

  expect(node.find('ColumnSwitch').prop('label')).toBe('dur1');
});

it('should create section for count columns', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{counts: {incidents: 1}}]},
        data,
      }}
    />
  );

  expect(node.find('CollapsibleSection').prop('sectionKey')).toBe('counts');
  expect(node.find('ColumnSwitch').length).toBe(1);
  expect(node.find('ColumnSwitch').prop('label')).toBe('Incidents');
});

it('should disable the switch and set the tooltip message when disabled', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{counts: {incidents: 1}}]},
        data,
      }}
      disabled
    />
  );

  expect(node.find('FormGroup').childAt(0).text()).toContain(
    'This function only works with automatic preview updates turned on.'
  );
  expect(node.find('AllColumnsButtons')).not.toExist();
});
