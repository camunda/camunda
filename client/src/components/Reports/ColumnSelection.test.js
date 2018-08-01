import React from 'react';
import {mount} from 'enzyme';

import ColumnSelectionAddon from './ColumnSelection';

const ColumnSelection = ColumnSelectionAddon.Content;

jest.mock('components', () => {
  return {
    Popover: ({children}) => <div>{children}</div>,
    Switch: props => <input type="checkbox" {...props} />
  };
});

jest.mock('./service', () => {
  return {isRawDataReport: () => true};
});

jest.mock('services', () => {
  return {
    formatters: {
      convertCamelToSpaces: jest.fn().mockReturnValue('Process Definition Key')
    }
  };
});

it('should have a switch for every column + all columns switch', () => {
  const node = mount(
    <ColumnSelection
      report={{result: [{a: 1, b: 2, c: 3, variables: {x: 1, y: 2}}]}}
      data={{configuration: {}}}
    />
  );

  expect(node.find('input[type="checkbox"]').length).toBe(6);
});

it('should call the onChange handler', () => {
  const spy = jest.fn();
  const node = mount(
    <ColumnSelection
      report={{result: [{a: 1, variables: {}}]}}
      data={{configuration: {}}}
      change={() => spy}
    />
  );
  node
    .find('input[type="checkbox"]')
    .at(1)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith(['a']);
});

it('should change the switches labels to space case instead of camelCase for non variables', () => {
  const node = mount(
    <ColumnSelection
      report={{result: [{processDefinitionKey: 1, variables: {testVariable: 1}}]}}
      data={{configuration: {}}}
    />
  );
  expect(node.find('.ColumnSelection__entry').at(1)).toIncludeText('Process Definition Key');
  expect(node.find('.ColumnSelection__entry').at(2)).toIncludeText('Variable: testVariable');
});

it('should call change with all column names when all columns switch is disabled', () => {
  const spy = jest.fn();
  const node = mount(
    <ColumnSelection
      report={{result: [{a: 1, b: 3, variables: {}}]}}
      data={{configuration: {}}}
      change={() => spy}
    />
  );
  node
    .find('input[type="checkbox"]')
    .at(0)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith(['a', 'b']);
});

it('should call change with an empty array when all columns switch is enabled', () => {
  const spy = jest.fn();
  const node = mount(
    <ColumnSelection
      report={{result: [{a: 1, b: 3, variables: {}}]}}
      data={{configuration: {}}}
      change={() => spy}
    />
  );
  node
    .find('input[type="checkbox"]')
    .at(0)
    .simulate('change', {target: {checked: false}});

  node
    .find('input[type="checkbox"]')
    .at(0)
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith([]);
});
