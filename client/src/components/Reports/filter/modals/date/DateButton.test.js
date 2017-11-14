import React from 'react';

import DateButton from './DateButton';

import {mount} from 'enzyme';
import moment from 'moment';

jest.mock('components', () => {return {
  Button: props => <button onClick={props.onClick}>{props.children}</button>
}});

it('should contain a button', () => {
  const node = mount(<DateButton dateLabel={DateButton.TODAY} />)

  expect(node.find('button')).toBePresent();
});

it('should set label on element', () => {
  const node = mount(<DateButton dateLabel={DateButton.TODAY} />)

  expect(node).toIncludeText(DateButton.TODAY);
});

it('should set dates on click', () => {
  const spy = jest.fn();
  const node = mount(<DateButton dateLabel={DateButton.TODAY} setDates={spy} />)

  const today = moment();

  node.find('button').simulate('click');

  const {startDate, endDate} = spy.mock.calls[0][0];

  expect(startDate.format('YYYY-MM-DD')).toEqual(today.format('YYYY-MM-DD'));
  expect(endDate.format('YYYY-MM-DD')).toEqual(today.format('YYYY-MM-DD'));
});
