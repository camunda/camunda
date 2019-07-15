/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Typeahead from './Typeahead';
import {Dropdown, Input} from 'components';

import {shallow} from 'enzyme';

it('should display available options if dropdown is open', () => {
  const node = shallow(<Typeahead values={['foo', 'bar']} />);

  node.setState({
    optionsVisible: true
  });

  expect(node).toMatchSnapshot();
});

it('should format the data based on the provided formatter', () => {
  const node = shallow(<Typeahead values={['foo', 'bar']} formatter={v => v + v} />);

  node.setState({
    optionsVisible: true
  });

  expect(node).toMatchSnapshot();
});

it('should only display entries that match the typeahead value', () => {
  const node = shallow(<Typeahead values={['varFoo', 'varBar', 'varFoobar']} />);

  node.setState({
    optionsVisible: true,
    query: 'foo'
  });

  expect(node).toMatchSnapshot();
});

it('should only display entries that match the typeahead value, even if there is a formatter', () => {
  const node = shallow(
    <Typeahead values={['varFoo', 'varBar', 'varFoobar']} formatter={v => v + v} />
  );

  node.setState({
    optionsVisible: true,
    query: 'foobarvar'
  });

  expect(node).toMatchSnapshot();
});

it('should call the provided onSelect method when a selection is done', () => {
  const spy = jest.fn();
  const node = shallow(<Typeahead values={['foo', 'bar']} onSelect={spy} />);

  node.setState({
    optionsVisible: true
  });

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalledWith('foo');
});

it('should reset the query to the latest committed state when the input field blurs', () => {
  const node = shallow(<Typeahead values={['foo', 'bar']} />);

  node.setState({
    optionsVisible: true,
    lastCommittedValue: 'foo',
    query: 'I typed something in the typeahead'
  });

  node.find(Input).simulate('blur');

  expect(node).toHaveState('query', 'foo');
});

it('should disable the input field when disabled prop is set to true', () => {
  const node = shallow(<Typeahead values={['foo', 'bar']} disabled={true} />);

  expect(node.find(Input)).toHaveProp('disabled', true);
});

it('should show the dropdown menu options on input focus', () => {
  const node = shallow(<Typeahead values={['foo', 'bar']} />);
  node.find(Input).prop('onFocus')();

  expect(node).toHaveState('optionsVisible', true);
});

it('should show a no results message if no values are provided', () => {
  const node = shallow(<Typeahead values={[]} noValuesMessage="No reports have been created" />);

  expect(node.find(Input)).toBeDisabled();
  expect(node.find(Input)).toHaveValue('No reports have been created');
});

it('should show the initial value if provided on mount', () => {
  const node = shallow(<Typeahead initialValue="foo" values={['bar']} formatter={v => v} />);

  expect(node.find(Input)).toHaveValue('foo');
});
