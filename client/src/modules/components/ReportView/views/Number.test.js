import React from 'react';
import {mount} from 'enzyme';

import Number from './Number';

jest.mock('services', () => {
  return {
    formatters: {
      convertDurationToSingleNumber: () => 12
    },
    isDurationValue: data => typeof data !== 'number'
  };
});

jest.mock('./ProgressBar', () => () => <div>ProgressBar</div>);

it('should display the number provided per data property', () => {
  const node = mount(<Number data={1234} formatter={v => v} />);

  expect(node).toIncludeText('1234');
});

it('should display an error message if the data does not have the correct format', () => {
  const node = mount(<Number data={{foo: 'bar'}} errorMessage="Error" formatter={v => v} />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Number errorMessage="Error" formatter={v => v} />);

  expect(node).toIncludeText('Error');
});

it('should not display an error message if data is valid', () => {
  const node = mount(<Number data={123} errorMessage="Error" formatter={v => v} />);

  expect(node).not.toIncludeText('Error');
});

it('should format data according to the provided formatter', () => {
  const node = mount(<Number data={123} formatter={v => 2 * v} />);

  expect(node).toIncludeText('246');
});

it('should display a progress bar if target values are active', () => {
  const node = mount(
    <Number data={123} formatter={v => 2 * v} targetValue={{active: true, values: {}}} />
  );

  expect(node).toIncludeText('ProgressBar');
});
