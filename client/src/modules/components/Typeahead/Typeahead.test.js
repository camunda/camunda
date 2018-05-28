import React from 'react';

import Typeahead from './Typeahead';

import {mount} from 'enzyme';

it('should initially load available options with empty string as query', () => {
  const spy = jest.fn();
  mount(<Typeahead getValues={spy} />);
  expect(spy).toHaveBeenCalledWith('');
});

it('should display available options if dropdown is open', async () => {
  const getValues = jest.fn();
  getValues.mockReturnValue([{name: 'foo', type: 'Boolean'}, {name: 'bar', type: 'Integer'}]);
  const nameRenderer = variable => {
    return variable.name;
  };
  const node = mount(<Typeahead getValues={getValues} nameRenderer={nameRenderer} />);

  await node
    .find('.Typeahead__input')
    .first()
    .simulate('click');

  node.setState({
    optionsVisible: true
  });

  expect(node.find('.Typeahead__search-result').at(0)).toIncludeText('foo');
  expect(node.find('.Typeahead__search-result').at(3)).toIncludeText('bar');
});
