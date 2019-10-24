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
  const node = shallow(
    <Typeahead
      values={['foo', 'bar']}
      formatter={v => ({text: v + v, tag: ' (User Group)', subTexts: [null, 'id']})}
    />
  );

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
    <Typeahead values={['varFoo', 'varBar', 'varFoobar']} formatter={v => ({text: v + v})} />
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
  const node = shallow(<Typeahead initialValue="foo" values={['bar']} />);

  expect(node.find(Input)).toHaveValue('foo');
});

it('should show no results options if there are not results', () => {
  const node = shallow(<Typeahead values={['a']} />);

  node
    .find(Input)
    .props()
    .onChange({target: {value: 'not found value'}});

  expect(node.find('.searchResult')).toMatchSnapshot();
});

it('should show an info message if there are more result than the shown', async () => {
  const node = shallow(<Typeahead values={() => ({result: ['a'], total: 25})} />);

  await node.update();
  node.find(Input).prop('onFocus')();

  expect(node.find('.searchResult')).toMatchSnapshot();
});

it('should call getValue to filter and render the data when available', async () => {
  const spy = jest.fn().mockReturnValue({result: ['item1', 'item2']});
  const node = shallow(<Typeahead values={spy} />);

  await node
    .find(Input)
    .props()
    .onChange({target: {value: 'test'}});

  node.instance().loadNewValues.flush();

  expect(spy).toHaveBeenCalledWith('test');
  await node.update();
  expect(node).toMatchSnapshot();
});
