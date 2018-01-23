import React from 'react';

import Filter from './Filter';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Dropdown = ({children}) => <p id="dropdown">Dropdown: {children}</p>;
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

it('should contain an edit filter modal when a filter should be edited', () => {
  const node = mount(<Filter data={[{type:'rollingDate'}]} />);

  node.instance().openEditFilterModal([{
        data: {
          operator : "bar",
          type : "baz",
          value : "foo"
        },
        type : "date"
    }])();

  expect(node).toIncludeText('DateFilter');
});

it('should contain a FilterModal component based on the selected new Filter', () => {
  const node = mount(<Filter data={[]} />);

  node.instance().openNewFilterModal('variable')();

  expect(node).toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should contain a EditFilterModal component based on the Filter selected for edition', () => {
  const node = mount(<Filter data={[{type: 'variable'}]} />);

  node.instance().openEditFilterModal([{
        data: {
          operator : "bar",
          type : "baz",
          value : "foo"
        },
        type : "variable"
    }])();
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

it('should edit the edited filter', () => {
  const spy = jest.fn();
  const sampleFilter = {
      data: {
        operator : "bar",
        type : "baz",
        value : "foo"
      },
      type : "qux"
  }

  const filters = [sampleFilter, 'foo'];
  const node = mount(<Filter data={filters} onChange={spy} />);

  node.instance().setState({
    editFilter: [sampleFilter]
  })

  node.instance().editFilter('bar');

  expect(spy.mock.calls[0][1]).toEqual(['bar', 'foo']);
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

  const buttons = node.find("#dropdown button");
  expect(buttons.find('[children="Start Date"]').prop("disabled")).toBeFalsy();
  expect(buttons.find('[children="Variable"]').prop("disabled")).toBeTruthy();
  expect(buttons.find('[children="Flow Node"]').prop("disabled")).toBeTruthy();
});

it('should remove any previous date and rolling date filters when adding a new date filter', () => {
  const spy = jest.fn();
  const previousFilters = [{type: 'date'}, {type: 'rollingDate'}];

  const node = mount(<Filter data={previousFilters} onChange={spy} />);

  node.instance().addFilter({type:'date', value: 'new date'});

  expect(spy.mock.calls[0][1]).toEqual([{type:'date', value: 'new date'}]);
});
