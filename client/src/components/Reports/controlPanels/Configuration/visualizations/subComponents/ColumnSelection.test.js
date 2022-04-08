/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';
import {getVariableLabel} from 'variables';

import ColumnSelection from './ColumnSelection';

jest.mock('variables', () => ({
  getVariableLabel: jest.fn(),
}));

const data = {
  configuration: {
    tableColumns: {
      includeNewVariables: true,
      includedColumns: ['processDefinitionKey', 'processInstanceId'],
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
            },
          ],
        },
        data,
      }}
    />
  );

  expect(node.find('Switch').length).toBe(5);
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

  expect(node.find('.columnSelectionSwitch').at(0).prop('label').props.children).toContain(
    'Process Definition Key'
  );
  expect(node.find('.columnSelectionSwitch').at(1).prop('label').props.children).toMatchSnapshot();
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

it('should provde a sane interface for decision tables', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        data,
        result: {
          data: [
            {
              decisionDefinitionId: 'foo',
              decisionDefinitionKey: 'bar',
              inputVariables: {
                crypticId: {name: 'Cool Name'},
              },
              outputVariables: {
                clause7: {name: 'Klaus Seven'},
              },
            },
          ],
        },
      }}
    />
  );

  expect(node).toMatchSnapshot();
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

  node.find(LabeledInput).simulate('change', {target: {checked: false}});

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

  expect(node.find('Switch').prop('label').props.children[1]).toBe('variableLabel');
});
