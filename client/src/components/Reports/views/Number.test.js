import React from 'react';
import {mount} from 'enzyme';

import Number from './Number';

it('should display the number provided per data property', () => {
  const node = mount(<Number data={1234} />);

  expect(node).toIncludeText('1234');
});

it('should display an error message if the data does not have the correct format', () => {
  const node = mount(<Number data={{foo: 'bar'}} errorMessage='Error' />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Number errorMessage='Error' />);

  expect(node).toIncludeText('Error');
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Number data={123} errorMessage='Error' />);

  expect(node).not.toIncludeText('Error');
});
