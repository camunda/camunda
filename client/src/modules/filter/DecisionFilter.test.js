/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import DecisionFilter from './DecisionFilter';
import FilterList from './FilterList';
import {Dropdown} from 'components';
import {DateFilter, VariableFilter} from './modals';

import {shallow} from 'enzyme';

it('should contain a list of Filters', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  expect(node.find(FilterList)).toExist();
});

it('should contain a dropdown', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  expect(node.find(Dropdown)).toExist();
});

it('should not contain any filter modal when no newFilter is selected', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  expect(node.find(DateFilter)).not.toExist();
  expect(node.find(VariableFilter)).not.toExist();
});

it('should contain a filter modal when a newFilter should be created', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  node.instance().openNewFilterModal('evaluationDateTime')();

  expect(node.find(DateFilter)).toExist();
});

it('should contain an edit filter modal when a filter should be edited', () => {
  const node = shallow(<DecisionFilter data={[{type: 'evaluationDateTime'}]} />);

  node.instance().openEditFilterModal({
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'evaluationDateTime',
  })();

  expect(node.find(DateFilter)).toExist();
});

it('should contain a FilterModal component based on the selected new Filter', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  node.instance().openNewFilterModal('inputVariable')();

  expect(node.find(VariableFilter)).toExist();
  expect(node.find(DateFilter)).not.toExist();
});

it('should contain a EditFilterModal component based on the Filter selected for edition', () => {
  const node = shallow(<DecisionFilter data={[{type: 'evaluationDateTime'}]} />);

  node.instance().openEditFilterModal({
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'evaluationDateTime',
  })();
  expect(node.find(DateFilter)).toExist();
  expect(node.find(VariableFilter)).not.toExist();
});

it('should add a filter to the list of filters', () => {
  const spy = jest.fn();
  const sampleFilter = {
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'qux',
  };
  const previousFilters = [sampleFilter];

  const node = shallow(<DecisionFilter data={previousFilters} onChange={spy} />);

  node.instance().addFilter('Filter 2');

  expect(spy.mock.calls[0][0].filter).toEqual({$set: [sampleFilter, 'Filter 2']});
});

it('should edit the edited filter', () => {
  const spy = jest.fn();
  const sampleFilter = {
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'qux',
  };

  const filters = [sampleFilter, 'foo'];
  const node = shallow(<DecisionFilter data={filters} onChange={spy} />);

  node.instance().setState({
    editFilter: sampleFilter,
  });

  node.instance().editFilter('bar');

  expect(spy.mock.calls[0][0].filter).toEqual({0: {$set: 'bar'}});
});

it('should remove a filter from the list of filters', () => {
  const spy = jest.fn();
  const previousFilters = ['Filter 1', 'Filter 2', 'Filter 3'];

  const node = shallow(<DecisionFilter data={previousFilters} onChange={spy} />);

  node.instance().deleteFilter('Filter 2');

  expect(spy.mock.calls[0][0].filter).toEqual({$set: ['Filter 1', 'Filter 3']});
});

it('should disable variable filters if no decision definition is available', () => {
  const node = shallow(<DecisionFilter data={[]} />);

  const buttons = node.find(Dropdown.Option);
  expect(buttons.find('[children="Evaluation Date Time"]').prop('disabled')).toBeFalsy();
  expect(buttons.find('[children="Input Variable"]').prop('disabled')).toBeTruthy();
  expect(buttons.find('[children="Output Variable"]').prop('disabled')).toBeTruthy();
});

it('should remove any previous evaluationDateTime filters when adding a new date filter', () => {
  const spy = jest.fn();
  const previousFilters = [{type: 'evaluationDateTime'}];

  const node = shallow(<DecisionFilter data={previousFilters} onChange={spy} />);

  node.instance().addFilter({type: 'evaluationDateTime', value: 'new date'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    $set: [{type: 'evaluationDateTime', value: 'new date'}],
  });
});
