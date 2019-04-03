/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateFilter from './DateFilter';
import {mount} from 'enzyme';

console.error = jest.fn();

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    Message: props => <div {...props}>{props.children}</div>,
    Button: props => <button {...props}>{props.children}</button>,
    ButtonGroup: props => <div {...props}>{props.children}</div>,
    Input: props => <input {...props} />,
    DatePicker: props => <div>DatePicker</div>,
    Labeled: props => (
      <div>
        <label id={props.id}>{props.label}</label>
        {props.children}
      </div>
    ),
    Select
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      camelCaseToLabel: text => text
    }
  };
});

it('should contain a modal', () => {
  const node = mount(<DateFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should contain a Date Picker', () => {
  const node = mount(<DateFilter />);

  expect(node).toIncludeText('DatePicker');
});

it('should pass the onDateChangeFunction to the DatePicker', () => {
  const node = mount(<DateFilter />);

  expect(node.find('DatePicker')).toHaveProp('onDateChange');
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = mount(<DateFilter close={spy} />);

  const abortButton = node.find('#modal_actions button').at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = mount(<DateFilter addFilter={spy} />);
  node.setState({
    validDate: true
  });
  const addButton = node.find('#modal_actions button').at(1);

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should allow switching between static date and dynamic date mode', () => {
  const node = mount(<DateFilter />);

  expect(node.find('div.DateFilter__mode-buttons')).toIncludeText('Fixed');
  expect(node.find('div.DateFilter__mode-buttons')).toIncludeText('Relative');
});

it('should disable the add filter button when dynamic value is not valid', () => {
  const node = mount(<DateFilter />);

  node.instance().setDynamicValue({target: {value: 'asd'}});

  expect(node.state('validDate')).toBe(false);
});

it('shoud show a warning message if the modal is for end date filter', () => {
  const endDateModal = mount(<DateFilter filterType="endDate" />);

  expect(endDateModal.find('Message')).toBePresent();
});

it('shoud make difference between start and end date filter modals', () => {
  const endDateModal = mount(<DateFilter filterType="endDate" />);

  expect(endDateModal).toIncludeText('Add endDate Filter');
  endDateModal.instance().setState({
    mode: 'dynamic'
  });
  expect(endDateModal).toIncludeText('Only include process instances ended within the last');

  const startDateModal = mount(<DateFilter filterType="startDate" />);

  expect(startDateModal).toIncludeText('Add startDate Filter');
  startDateModal.instance().setState({
    mode: 'dynamic'
  });
  expect(startDateModal).toIncludeText('Only include process instances started within the last');
});
