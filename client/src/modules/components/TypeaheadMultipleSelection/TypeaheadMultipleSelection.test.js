import React from 'react';

import TypeaheadMultipleSelection from './TypeaheadMultipleSelection';

import {mount} from 'enzyme';

it('should still contain selected value after changing the prefix', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setPrefix = jest.fn();
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setPrefix={setPrefix}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementation(value =>
    node.setProps({selectedValues: node.props().selectedValues.concat([value])})
  );
  setPrefix.mockImplementation(prefix =>
    node.setProps({
      availableValues: allValues.filter(value => value.slice(0, prefix.length) === prefix)
    })
  );

  node.props().setPrefix('a');
  node.props().toggleValue('asd');
  node.props().setPrefix('f');
  expect(node).toIncludeText('asd');
  expect(node).toIncludeText('fefwf');
});

it('it should not show the unselected value if it does not match the query', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setPrefix = jest.fn();
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setPrefix={setPrefix}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementation(value =>
    node.setProps({
      selectedValues: node.props().selectedValues.includes(value)
        ? node.props().selectedValues.filter(v => v !== value)
        : node.props().selectedValues.concat([value])
    })
  );
  setPrefix.mockImplementation(prefix =>
    node.setProps({
      availableValues: allValues.filter(value => value.slice(0, prefix.length) === prefix)
    })
  );

  node.props().setPrefix('a');
  node.props().toggleValue('asd');
  node.props().setPrefix('f');
  node.props().toggleValue('asd');
  expect(node).toIncludeText('fefwf');
  expect(node).not.toIncludeText('asd');
});

it('should add a value to the list of values when the checkbox is clicked', async () => {
  const toggleValue = jest.fn();
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      selectedValues={[]}
      availableValues={allValues}
    />
  );
  toggleValue.mockImplementation(({target: {checked, value}}) =>
    node.setProps({
      selectedValues: node.props().selectedValues.includes(value)
        ? node.props().selectedValues.filter(v => v !== value)
        : node.props().selectedValues.concat([value])
    })
  );
  node
    .find('input[type="checkbox"]')
    .at(1)
    .simulate('change', {target: {checked: true, value: 'value2'}});

  expect(node.props().selectedValues.includes('value2')).toBe(true);
});

it('should request the values filtered by prefix entered in the input', () => {
  const setPrefix = jest.fn();
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = mount(
    <TypeaheadMultipleSelection
      setPrefix={setPrefix}
      selectedValues={[]}
      availableValues={allValues}
    />
  );
  setPrefix.mockImplementation(({target: {value}}) =>
    node.setProps({
      availableValues: allValues.filter(v => v.slice(0, value.length) === value)
    })
  );

  node
    .find('.TypeaheadMultipleSelection__input')
    .first()
    .simulate('change', {target: {value: 't'}});

  expect(node.props().availableValues.length).toBe(1);
  expect(node.props().availableValues[0]).toBe('thdfhr');
});
