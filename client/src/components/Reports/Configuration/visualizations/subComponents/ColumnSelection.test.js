import React from 'react';
import {shallow} from 'enzyme';

import ColumnSelection from './ColumnSelection';

it('should have a switch for every column + all columns switch', () => {
  const node = shallow(
    <ColumnSelection
      report={{result: [{a: 1, b: 2, c: 3, variables: {x: 1, y: 2}}], data: {configuration: {}}}}
    />
  );

  expect(node.find('Switch').length).toBe(6);
});

it('should call the onChange handler', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      report={{result: [{a: 1, variables: {}}], data: {configuration: {}}}}
      onChange={spy}
    />
  );
  node
    .find('Switch')
    .at(1)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith('excludedColumns', ['a']);
});

it('should change the switches labels to space case instead of camelCase for non variables', () => {
  const node = shallow(
    <ColumnSelection
      report={{
        result: [{processDefinitionKey: 1, variables: {testVariable: 1}}],
        data: {configuration: {}}
      }}
    />
  );
  expect(node.find('.ColumnSelection__entry').at(1)).toIncludeText('Process Definition Key');
  expect(node.find('.ColumnSelection__entry').at(2)).toIncludeText('Variable: testVariable');
});

it('should call change with all column names when all columns switch is disabled', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      report={{result: [{a: 1, b: 3, variables: {}}], data: {configuration: {}}}}
      onChange={spy}
    />
  );
  node
    .find('Switch')
    .at(0)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith('excludedColumns', ['a', 'b']);
});

it('should call change with an empty array when all columns switch is enabled', () => {
  const spy = jest.fn();
  const node = shallow(
    <ColumnSelection
      report={{result: [{a: 1, b: 3, variables: {}}], data: {configuration: {}}}}
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

  expect(spy).toHaveBeenCalledWith('excludedColumns', []);
});
