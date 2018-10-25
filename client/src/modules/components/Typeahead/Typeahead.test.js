import React from 'react';

import Typeahead from './Typeahead';
import {Dropdown, Input} from 'components';

import {mount} from 'enzyme';

it('should display available options if dropdown is open', () => {
  const node = mount(<Typeahead values={['foo', 'bar']} />);

  node.setState({
    optionsVisible: true
  });

  expect(node.find(Dropdown.Option).at(0)).toIncludeText('foo');
  expect(node.find(Dropdown.Option).at(1)).toIncludeText('bar');
});

it('should format the data based on the provided formatter', () => {
  const node = mount(<Typeahead values={['foo', 'bar']} formatter={v => v + v} />);

  node.setState({
    optionsVisible: true
  });

  expect(node.find(Dropdown.Option).at(0)).toIncludeText('foofoo');
  expect(node.find(Dropdown.Option).at(1)).toIncludeText('barbar');
});

it('should only display entries that match the typeahead value', () => {
  const node = mount(<Typeahead values={['varFoo', 'varBar', 'varFoobar']} />);

  node.setState({
    optionsVisible: true,
    query: 'foo'
  });

  expect(node.find(Dropdown.Option).at(0)).toIncludeText('varFoo');
  expect(node.find(Dropdown.Option).at(1)).toIncludeText('varFoobar');
  expect(node).not.toIncludeText('varBar');
});

it('should only display entries that match the typeahead value, even if there is a formatter', () => {
  const node = mount(
    <Typeahead values={['varFoo', 'varBar', 'varFoobar']} formatter={v => v + v} />
  );

  node.setState({
    optionsVisible: true,
    query: 'foobarvar'
  });

  expect(node.find(Dropdown.Option).at(0)).toIncludeText('varFoobarvarFoobar');
});

it('should call the provided onSelect method when a selection is done', () => {
  const spy = jest.fn();
  const node = mount(<Typeahead values={['foo', 'bar']} onSelect={spy} />);

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
  const node = mount(<Typeahead values={['foo', 'bar']} />);

  node.setState({
    optionsVisible: true,
    lastCommittedValue: 'foo',
    query: 'I typed something in the typeahead'
  });

  node.find(Input).simulate('blur');

  expect(node).toHaveState('query', 'foo');
});

it('should disable the input field when disabled prop is set to true', () => {
  const node = mount(<Typeahead values={['foo', 'bar']} disabled={true} />);

  expect(node.find(Input)).toHaveProp('disabled', true);
});

it('should show the dropdown menu options on input focus', () => {
  const node = mount(<Typeahead values={['foo', 'bar']} />);
  node.find(Input).prop('onFocus')();

  expect(node).toHaveState('optionsVisible', true);
});
