import React from 'react';
import {mount} from 'enzyme';

import Number from './Number';

it('should display the number provided per data property', () => {
  const node = mount(<Number data={1234} />);

  expect(node).toIncludeText('1234');
});

it('should display an error message if the data does not have the correct format', () => {
  const node = mount(<Number data={{foo: 'bar'}} />);

  expect(node).toIncludeText('Cannot display data');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Number />);

  expect(node).toIncludeText('Cannot display data');
});
