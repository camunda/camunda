/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import TypeaheadMultipleSelection from './TypeaheadMultipleSelection';

import {shallow} from 'enzyme';

it('should still contain selected value after changing the filter', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setFilter = jest.fn();
  const node = shallow(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementation(
    (value) => () =>
      node.setProps({selectedValues: node.instance().props.selectedValues.concat([value])})
  );

  setFilter.mockImplementation((filter) =>
    node.setProps({
      availableValues: allValues.filter((value) => value.slice(0, filter.length) === filter),
    })
  );

  node.instance().props.setFilter('a');
  node.instance().props.toggleValue('asd')();
  node.instance().props.setFilter('f');
  expect(node.find({label: 'asd'})).toExist();
  expect(node.find({label: 'fefwf'})).toExist();
});

it('it should not show the unselected value if it does not match the query', async () => {
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const toggleValue = jest.fn();
  const setFilter = jest.fn();
  const node = shallow(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  toggleValue.mockImplementationOnce(
    (value) => () =>
      node.setProps({
        selectedValues: node.instance().props.selectedValues.includes(value)
          ? node.instance().props.selectedValues.filter((v) => v !== value)
          : node.instance().props.selectedValues.concat([value]),
      })
  );
  setFilter.mockImplementation((filter) =>
    node.setProps({
      availableValues: allValues.filter((value) => value.slice(0, filter.length) === filter),
    })
  );

  node.instance().props.setFilter('a');
  node.instance().props.toggleValue('asd');
  node.instance().props.setFilter('f');
  node.instance().props.toggleValue('asd');

  expect(node.find({label: 'asd'})).not.toExist();
  expect(node.find({label: 'fefwf'})).toExist();
});

it('should add a value to the list of values when the checkbox is clicked', async () => {
  const toggleValue = (value) =>
    node.setProps({
      selectedValues: node.instance().props.selectedValues.includes(value)
        ? node.instance().props.selectedValues.filter((v) => v !== value)
        : node.instance().props.selectedValues.concat([value]),
    });

  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = shallow(
    <TypeaheadMultipleSelection
      toggleValue={toggleValue}
      selectedValues={[]}
      availableValues={allValues}
    />
  );

  node.instance().toggleAvailable({target: {checked: true, value: '1'}});

  expect(node.instance().props.selectedValues.includes('dhdf')).toBe(true);
});

it('should request the values filtered by filter entered in the input', () => {
  const setFilter = jest.fn();
  const allValues = ['asd', 'dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = shallow(
    <TypeaheadMultipleSelection
      setFilter={setFilter}
      selectedValues={[]}
      availableValues={allValues}
      toggleValue={() => {}}
    />
  );
  setFilter.mockImplementation((value) =>
    node.setProps({
      availableValues: allValues.filter((v) => v.slice(0, value.length) === value),
    })
  );

  node
    .find('.TypeaheadMultipleSelection__input')
    .first()
    .simulate('change', {target: {value: 't'}});

  expect(node.instance().props.availableValues.length).toBe(1);
  expect(node.instance().props.availableValues[0]).toBe('thdfhr');
});

it('make the list items draggable when adding a drag handler', () => {
  const setFilter = jest.fn();
  const allValues = ['dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const node = shallow(
    <TypeaheadMultipleSelection
      setFilter={setFilter}
      selectedValues={['a']}
      availableValues={allValues}
      toggleValue={() => {}}
      onOrderChange={() => {}}
    />
  );
  setFilter.mockImplementation(({target: {value}}) =>
    node.setProps({
      availableValues: allValues.filter((v) => v.slice(0, value.length) === value),
    })
  );

  expect(node.find('.TypeaheadMultipleSelection__valueListItem').first().props().draggable).toBe(
    true
  );

  expect(node.find('.TypeaheadMultipleSelection__valueListItem').first()).toHaveClassName(
    'draggable'
  );
});

it('make invok onOrderChange with the new selectedvalues data on drag end', () => {
  const setFilter = jest.fn();
  const allValues = ['dhdf', 'fefwf', 'aaf', 'thdfhr'];
  const spy = jest.fn();
  const node = shallow(
    <TypeaheadMultipleSelection
      setFilter={setFilter}
      selectedValues={['a', 'b']}
      availableValues={allValues}
      toggleValue={() => {}}
      onOrderChange={spy}
    />
  );
  setFilter.mockImplementation(({target: {value}}) =>
    node.setProps({
      availableValues: allValues.filter((v) => v.slice(0, value.length) === value),
    })
  );

  const dragStartEvt = {
    currentTarget: {
      getBoundingClientRect: () => ({height: '30px'}),
      parentNode: {
        removeChild: () => {},
        contains: () => false,
      },
      dataset: {
        id: 1,
      },
      style: {
        display: '',
      },
    },
    dataTransfer: {
      effectAllowed: '',
      setData: () => {},
    },
  };

  const dragOverEvt = {
    preventDefault: () => {},
    target: {
      className: 'test',
      nodeName: 'LI',
      closest: () => ({
        parentNode: {
          insertBefore: () => {},
        },
        dataset: {
          id: 0,
        },
      }),
    },
  };

  node.instance().dragStart(dragStartEvt);
  node.instance().dragOver(dragOverEvt);
  node.instance().dragEnd(dragOverEvt);

  expect(spy).toHaveBeenCalledWith(['b', 'a']);
});

it('should hide search input from typeahead if specified', () => {
  const node = shallow(
    <TypeaheadMultipleSelection
      selectedValues={[]}
      availableValues={[]}
      toggleValue={() => {}}
      hideSearch
    />
  );

  expect(node.find('.TypeaheadMultipleSelection__input')).not.toExist();
});
