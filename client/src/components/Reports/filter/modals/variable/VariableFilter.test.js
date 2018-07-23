import React from 'react';

import VariableFilter from './VariableFilter';

import {DateInput} from './date';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Typeahead = props => {
    const allowedProps = {...props};
    delete allowedProps.selectValue;
    delete allowedProps.initialValue;
    delete allowedProps.getValues;
    delete allowedProps.nameRenderer;
    return <div {...allowedProps}>{props.children}</div>;
  };

  return {
    Modal,
    Typeahead,
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>
  };
});

jest.mock('./service', () => {
  const variables = [
    {name: 'boolVar', type: 'Boolean'},
    {name: 'numberVar', type: 'Float'},
    {name: 'stringVar', type: 'String'}
  ];

  return {
    loadVariables: jest.fn().mockReturnValue(variables)
  };
});

jest.mock('./boolean', () => {
  return {BooleanInput: () => 'BooleanInput'};
});
jest.mock('./number', () => {
  return {NumberInput: () => 'NumberInput'};
});
jest.mock('./string', () => {
  return {StringInput: () => 'StringInput'};
});
jest.mock('./date', () => {
  const DateInput = () => 'DateInput';

  DateInput.defaultFilter = {startDate: 'start', endDate: 'end'};
  DateInput.parseFilter = jest.fn();
  DateInput.addFilter = jest.fn();

  return {DateInput};
});

it('should contain a modal', () => {
  const node = mount(<VariableFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should disable add filter button if no variable is selected', () => {
  const node = mount(
    <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
  );

  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
});

it('should enable add filter button if variable selection is valid', async () => {
  const node = mount(
    <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
  );

  await node.setState({
    valid: true,
    selectedVariable: {type: 'String', name: 'StrVar'}
  });
  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeFalsy(); // create filter
});

it('should create a new filter', () => {
  const spy = jest.fn();
  const node = mount(
    <VariableFilter
      processDefinitionKey="procDefKey"
      processDefinitionVersion="1"
      addFilter={spy}
    />
  );

  node.setState({
    selectedVariable: {name: 'foo', type: 'String'},
    valid: true,
    filter: {
      operator: 'not in',
      values: ['value1', 'value2']
    }
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      data: {
        operator: 'not in',
        values: ['value1', 'value2']
      }
    }
  });
});

it('should use custom filter parsing logic from input components', () => {
  DateInput.parseFilter.mockClear();

  const existingFilter = {
    data: {
      type: 'Date',
      name: 'aDateVar',
      data: {
        type: 'static',
        start: 'someDate',
        end: 'someOtherDate'
      }
    }
  };
  mount(<VariableFilter filterData={existingFilter} />);

  expect(DateInput.parseFilter).toHaveBeenCalledWith(existingFilter);
});

it('should use custom filter adding logic from input components', () => {
  const spy = jest.fn();
  const node = mount(
    <VariableFilter
      processDefinitionKey="procDefKey"
      processDefinitionVersion="1"
      addFilter={spy}
    />
  );

  const selectedVariable = {name: 'foo', type: 'Date'};
  const filter = {startDate: 'start', endDate: 'end'};
  node.setState({
    selectedVariable,
    valid: true,
    filter
  });

  DateInput.addFilter.mockClear();

  node.find('button[type="primary"]').simulate('click');

  expect(DateInput.addFilter).toHaveBeenCalledWith(spy, selectedVariable, filter);
});
