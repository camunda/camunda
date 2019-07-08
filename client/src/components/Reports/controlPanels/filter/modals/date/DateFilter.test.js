/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateFilter from './DateFilter';
import {Modal, Button} from 'components';
import {shallow} from 'enzyme';

console.error = jest.fn();

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    formatters: {
      camelCaseToLabel: text => text
    }
  };
});

it('should contain a modal', () => {
  const node = shallow(<DateFilter />);

  expect(node.find('Modal')).toExist();
});

it('should contain a Date Picker', () => {
  const node = shallow(<DateFilter />);

  expect(node.find('DatePicker')).toExist();
});

it('should pass the onDateChangeFunction to the DatePicker', () => {
  const node = shallow(<DateFilter />);

  expect(node.find('DatePicker')).toHaveProp('onDateChange');
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = shallow(<DateFilter close={spy} addFilter={jest.fn()} />);

  const abortButton = node
    .find(Modal.Actions)
    .find(Button)
    .at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = shallow(<DateFilter addFilter={spy} />);
  node.setState({
    validDate: true
  });
  const addButton = node
    .find(Modal.Actions)
    .find(Button)
    .at(1);

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should allow switching between static date and dynamic date mode', () => {
  const node = shallow(<DateFilter />);

  expect(node.find('ButtonGroup')).toMatchSnapshot();
});

it('should disable the add filter button when dynamic value is not valid', () => {
  const node = shallow(<DateFilter />);

  node.instance().setDynamicValue({target: {value: 'asd'}});

  expect(node.state('validDate')).toBe(false);
});

it('shoud show a warning message if the modal is for end date filter', () => {
  const endDateModal = shallow(<DateFilter filterType="endDate" />);

  expect(endDateModal.find('Message')).toExist();
});

it('shoud make difference between start and end date filter modals', () => {
  const endDateModal = shallow(<DateFilter filterType="endDate" />);

  expect(endDateModal.find(Modal.Header).dive()).toIncludeText('Add endDate Filter');

  endDateModal.instance().setState({
    mode: 'dynamic'
  });

  expect(
    endDateModal
      .find(Modal.Header)
      .dive()
      .text()
  ).toBe('Add endDate Filter');

  const startDateModal = shallow(<DateFilter filterType="startDate" />);

  expect(
    startDateModal
      .find(Modal.Header)
      .dive()
      .text()
  ).toBe('Add startDate Filter');

  startDateModal.instance().setState({
    mode: 'dynamic'
  });
  expect(startDateModal.find('.tip')).toIncludeText('Only include process instances started:');
});
