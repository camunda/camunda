import React from 'react';

import TypeaheadMultipleSelection from './TypeaheadMultipleSelection';

import {mount} from 'enzyme';

jest.mock('services', () => {
  return {
    formatters: {
      getHighlightedText: text => text
    }
  };
});

it('should still contain selected value after changing the filter', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setFilter = jest.fn();
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementation(value => () =>
    node.setProps({selectedValues: node.props().selectedValues.concat([value])})
  );

  setFilter.mockImplementation(filter =>
    node.setProps({
      availableValues: allValues.filter(value => value.slice(0, filter.length) === filter)
    })
  );

  node.props().setFilter('a');
  node.props().toggleValue('asd')();
  node.props().setFilter('f');
  expect(node).toIncludeText('asd');
  expect(node).toIncludeText('fefwf');
});

it('it should not show the unselected value if it does not match the query', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setFilter = jest.fn();
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementationOnce(value => () =>
    node.setProps({
      selectedValues: node.props().selectedValues.includes(value)
        ? node.props().selectedValues.filter(v => v !== value)
        : node.props().selectedValues.concat([value])
    })
  );
  setFilter.mockImplementation(filter =>
    node.setProps({
      availableValues: allValues.filter(value => value.slice(0, filter.length) === filter)
    })
  );

  node.props().setFilter('a');
  node.props().toggleValue('asd');
  node.props().setFilter('f');
  node.props().toggleValue('asd');
  expect(node).toIncludeText('fefwf');
  expect(node).not.toIncludeText('asd');
});

it('should add a value to the list of values when the checkbox is clicked', async () => {
  const toggleValue = value => () =>
    node.setProps({
      selectedValues: node.props().selectedValues.includes(value)
        ? node.props().selectedValues.filter(v => v !== value)
        : node.props().selectedValues.concat([value])
    });

  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = mount(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      selectedValues={[]}
      availableValues={allValues}
    />
  );
  node
    .find('input[type="checkbox"]')
    .at(1)
    .simulate('change', {target: {checked: true}});

  expect(node.props().selectedValues.includes('dhdf')).toBe(true);
});

it('should request the values filtered by filter entered in the input', () => {
  const setFilter = jest.fn();
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = mount(
    <TypeaheadMultipleSelection
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
      toggleValue={() => {}}
    />
  );
  setFilter.mockImplementation(({target: {value}}) =>
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
