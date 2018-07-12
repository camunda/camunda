import React from 'react';

import DatePicker from './DatePicker';
import {mount} from 'enzyme';
import moment from 'moment';

console.error = jest.fn();

jest.mock('./DateFields', () => props => `DateFields: props: ${Object.keys(props)}`);
jest.mock('./DateButton', () => props => `DateButton: props: ${Object.keys(props)}`);

jest.mock('components', () => {
  return {
    ButtonGroup: props => <div {...props}>{props.children}</div>
  };
});

const startDate = moment([2017, 8, 29]);
const endDate = moment([2020, 6, 5]);

it('should contain date fields', () => {
  const node = mount(<DatePicker initialDates={{startDate, endDate}} />);

  expect(node).toIncludeText('DateFields');
});

// looks like enzyme does not find certain elements when they are rendered in a Fragment
// try again when this is closed: https://github.com/airbnb/enzyme/issues/1213
// it('should contain date buttons', () => {
//   const node = mount(<DatePicker />);

//   console.log(node.debug());

//   expect(node).toIncludeText('DateButton');
// });
