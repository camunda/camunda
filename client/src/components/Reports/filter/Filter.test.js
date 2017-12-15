import React from 'react';

import Filter from './Filter';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Dropdown = ({children}) => <p id="dropwdown">Dropdown: {children}</p>;
  Dropdown.Option = (props) => <button {...props}>{props.children}</button>;

  return {Dropdown};
});

jest.mock('./modals', () => {return {
  DateFilter: () => 'DateFilter',
  VariableFilter: () => 'VariableFilter',
  NodeFilter: () => 'NodeFilter'
}});

jest.mock('./FilterList', () => () => 'FilterList');

it('should contain a list of filters', () => {
  const node = mount(<Filter data={[]} />);

  expect(node).toIncludeText('FilterList');
});

it('should contain a dropdown', () => {
  const node = mount(<Filter data={[]} />);

  expect(node).toIncludeText('Dropdown');
});

it('should not contain any filter modal when no newFilter is selected', () => {
  const node = mount(<Filter data={[]} />);

  expect(node).not.toIncludeText('DateFilter');
  expect(node).not.toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('NodeFilter');
});

it('should contain a filter modal when a newFilter should be created', () => {
  const node = mount(<Filter data={[]} />);

  node.instance().openNewFilterModal('date')();

  expect(node).toIncludeText('DateFilter');
});

it('should contain a FilterModal component based on the selected new Filter', () => {
  const node = mount(<Filter data={[]} />);

  node.instance().openNewFilterModal('variable')();

  expect(node).toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should add a filter to the list of filters', () => {
  const spy = jest.fn();
  const sampleFilter = {
      data: {
        operator : "bar",
        type : "baz",
        value : "foo"
      },
      type : "qux"
  }
  const previousFilters = [sampleFilter];

  const node = mount(<Filter data={previousFilters} onChange={spy} />);

  node.instance().addFilter('Filter 2');

  expect(spy.mock.calls[0][1]).toEqual([sampleFilter, 'Filter 2']);
});

it('should add multiple filters to the list of filters', () => {
  const spy = jest.fn();
  const sampleFilter = {
      data: {
        operator : "bar",
        type : "baz",
        value : "foo"
      },
      type : "qux"
  };
  const previousFilters = [sampleFilter];

  const node = mount(<Filter data={previousFilters} onChange={spy} />);

  node.instance().addFilter('Filter 2', 'Filter 3');

  expect(spy.mock.calls[0][1]).toEqual([sampleFilter, 'Filter 2', 'Filter 3']);

});

it('should remove a filter from the list of filters', () => {
  const spy = jest.fn();
  const previousFilters = ['Filter 1', 'Filter 2', 'Filter 3'];

  const node = mount(<Filter data={previousFilters} onChange={spy} />);

  node.instance().deleteFilter('Filter 2');

  expect(spy.mock.calls[0][1]).toEqual(['Filter 1', 'Filter 3']);
});

it('should remove multiple filters from the list of filters', () => {
  const spy = jest.fn();
  const previousFilters = ['Filter 1', 'Filter 2', 'Filter 3'];

  const node = mount(<Filter data={previousFilters} onChange={spy} />);

  node.instance().deleteFilter('Filter 2', 'Filter 1');

  expect(spy.mock.calls[0][1]).toEqual(['Filter 3']);
});

it('should disable variable and executed flow node filter if no process definition is available', () => {
  const node = mount(<Filter processDefinitionId="" />);

  const buttons = node.find("#dropwdown button");
  expect(buttons.at(0).prop("disabled")).toBeFalsy(); // start date filter
  expect(buttons.at(1).prop("disabled")).toBeTruthy(); // variable filter
  expect(buttons.at(2).prop("disabled")).toBeTruthy(); // flow node filter
});
