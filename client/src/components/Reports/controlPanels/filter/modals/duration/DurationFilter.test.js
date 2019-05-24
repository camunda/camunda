/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DurationFilter from './DurationFilter';

import {mount} from 'enzyme';

jest.mock('services', () => {
  return {
    numberParser: {
      isValidNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value),
      isPositiveNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value) && +value > 0,
      isIntegerNumber: value => /^[+-]?\d+?$/.test(value),
      isFloatNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value)
    }
  };
});

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Message: props => <div {...props}>{props.children}</div>,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => {
      const allowedProps = {...props};
      delete allowedProps.isInvalid;
      return <input {...allowedProps} />;
    },
    Labeled: props => (
      <div>
        <label id={props.id}>{props.label}</label>
        {props.children}
      </div>
    ),
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    Select
  };
});

it('should contain a modal', () => {
  const node = mount(<DurationFilter />);

  expect(node.find('#modal')).toExist();
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = mount(<DurationFilter close={spy} />);

  const abortButton = node.find('#modal_actions button').at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have isInvalid prop on the input if value is invalid', async () => {
  const node = mount(<DurationFilter />);
  await node.setState({
    value: 'NaN'
  });

  expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = mount(<DurationFilter addFilter={spy} />);
  const addButton = node.find('#modal_actions button').at(1);

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should show a hint that only completed instances will be shown', () => {
  const node = mount(<DurationFilter />);
  expect(node.find('Message')).toExist();
});
