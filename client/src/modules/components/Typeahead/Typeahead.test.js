import React from 'react';

import Typeahead from './Typeahead';
import {Dropdown} from 'components';

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
