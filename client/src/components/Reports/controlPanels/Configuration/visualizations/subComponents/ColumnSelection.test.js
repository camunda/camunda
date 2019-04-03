/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ColumnSelection from './ColumnSelection';

it('should have a switch for every column + all columns switch', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{a: 1, b: 2, c: 3, variables: {x: 1, y: 2}}]},
        data: {configuration: {}}
      }}
    />
  );

  expect(node.find('Switch').length).toBe(5);
});

it('should change the switches labels to space case instead of camelCase for non variables', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: {data: [{processDefinitionKey: 1, variables: {testVariable: 1}}]},
        data: {configuration: {}}
      }}
    />
  );
  expect(node.find('.ColumnSelection__entry').at(0)).toIncludeText('Process Definition Key');
  expect(node.find('.ColumnSelection__entry').at(1)).toIncludeText('Variable: testVariable');
});

it('should call change with an empty array when all columns switch is enabled', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      report={{result: {data: [{a: 1, b: 3, variables: {}}]}, data: {configuration: {}}}}
      onChange={spy}
    />
  );
  node
    .find('Switch')
    .at(0)
    .simulate('change', {target: {checked: false}});

  node
    .find('Switch')
    .at(0)
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({excludedColumns: {$set: []}});
});

it('should provde a sane interface for decision tables', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        data: {configuration: {}},
        result: {
          data: [
            {
              decisionDefinitionId: 'foo',
              decisionDefinitionKey: 'bar',
              inputVariables: {
                crypticId: {name: 'Cool Name'}
              },
              outputVariables: {
                clause7: {name: 'Klaus Seven'}
              }
            }
          ]
        }
      }}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not crash if the report result is empty', () => {
  shallow(
    <ColumnSelection
      report={{
        data: {configuration: {}},
        result: {data: {}}
      }}
    />
  );
});
