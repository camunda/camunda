import React from 'react';

import VariableFilter from './VariableFilter';
import {loadVariables, loadValues} from './service';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => (
    <select {...props} id="select">
      {props.children}
    </select>
  );
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Select,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => {
      const allowedProps = {...props};
      delete allowedProps.isInvalid;
      return <input {...allowedProps} />;
    },
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    ControlGroup: props => <div>{props.children}</div>,
    ButtonGroup: props => <div {...props}>{props.children}</div>
  };
});

jest.mock('./service', () => {
  const variables = [
    {name: 'boolVar', type: 'Boolean'},
    {name: 'numberVar', type: 'Float'},
    {name: 'stringVar', type: 'String'}
  ];

  return {
    loadVariables: jest.fn().mockReturnValue(variables),
    loadValues: jest.fn().mockReturnValue(['val1', 'val2'])
  };
});

it('should contain a modal', () => {
  const node = mount(<VariableFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should initially load available variables', () => {
  mount(<VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />);

  expect(loadVariables).toHaveBeenCalledWith('procDefKey', '1');
});

it('should display available variables', () => {
  const node = mount(<VariableFilter processDefinitionKey="procDefKey" />);

  node.setState({
    variables: [{name: 'foo', type: 'String'}, {name: 'bar', type: 'String'}]
  });

  expect(node.find('option').at(1)).toIncludeText('foo');
  expect(node.find('option').at(2)).toIncludeText('bar');
});

it('should disable add filter button if no variable is selected', () => {
  const node = mount(
    <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
  );

  node.setState({
    variables: [{name: 'foo', type: 'String'}, {name: 'bar', type: 'String'}]
  });

  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
});

it('should enable add filter button if variable selection is valid', () => {
  const node = mount(
    <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
  );

  node.setState({
    variables: [{name: 'foo', type: 'String'}, {name: 'bar', type: 'String'}],
    values: ['bar'],
    selectedVariableIdx: 1
  });

  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeFalsy(); // create filter
});

describe('boolean variables', () => {
  it('should assume variable value true per default', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(node.state().values).toEqual([true]);
  });

  it('should show true and false operator fields', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}],
      selectedVariableIdx: 0
    });

    expect(node.find('button').at(0)).toIncludeText('true');
    expect(node.find('button').at(1)).toIncludeText('false');
  });

  it('should set the value when clicking on the operator fields', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}],
      selectedVariableIdx: 0
    });

    node
      .find('button')
      .at(1)
      .simulate('click');

    expect(node.state().values).toEqual([false]);
  });
});

describe('number variables', () => {
  it('should be initialized with an empty variable value', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(node.state().values).toEqual(['']);
  });

  it('should store the input in the state value array at the correct position', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0', 'value1', 'value2']
    });

    node
      .find('.VariableFilter__valueFields input')
      .at(1)
      .simulate('change', {
        target: {getAttribute: jest.fn().mockReturnValue(1), value: 'newValue'}
      });

    expect(node.state().values).toEqual(['value0', 'newValue', 'value2']);
  });

  it('should display the possibility to add another value', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0']
    });

    expect(node.find('.VariableFilter__addValueButton')).toBePresent();
  });

  it('should add another value when clicking add another value button', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0']
    });

    node.find('.VariableFilter__valueFields button').simulate('click');

    expect(node.state().values).toEqual(['value0', '']);
  });

  it('should not have the possibility to remove the value if there is only one value', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0']
    });

    expect(node.find('.VariableFilter__removeItemButton').exists()).toBeFalsy();
  });

  it('should have the possibility to remove a value if there are multiple values', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0', 'value1']
    });

    expect(node.find('.VariableFilter__removeItemButton button').length).toBe(2);
  });

  it('should disable add filter button if provided value is invalid', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['123xxxx']
    });

    const buttons = node.find('#modal_actions button');
    expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
    expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
  });

  it('should highlight value input if provided value is invalid', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['not a number']
    });

    expect(
      node
        .find('Input')
        .first()
        .props()
    ).toHaveProperty('isInvalid', true);
  });

  it('should disable add filter button if variable is integer but provided input is float', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      variables: [{name: 'foo', type: 'Integer'}],
      selectedVariableIdx: 0,
      values: ['123.23']
    });

    const buttons = node.find('#modal_actions button');
    expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
    expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
  });
});

describe('string variables', () => {
  it('should load 20 values initially', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'String'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(loadValues).toHaveBeenCalledWith('procDefKey', '1', 'foo', 'String', 0, 21);
  });

  it('should show available values', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: ['value1', 'value2', 'value3']
    });

    expect(node).toIncludeText('value1');
    expect(node).toIncludeText('value2');
    expect(node).toIncludeText('value3');
  });

  it('should add a value to the list of values when the checkbox is clicked', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: ['value1', 'value2', 'value3']
    });

    node
      .find('input[type="checkbox"]')
      .at(1)
      .simulate('change', {target: {checked: true, value: 'value2'}});

    expect(node.state().values).toEqual(['value2']);
  });

  it('should load 20 more values if the user wants more', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
      valuesAreComplete: false
    });

    node.find('.VariableFilter__valueFields button').simulate('click');

    expect(loadValues).toHaveBeenCalledWith('procDefKey', '1', 'foo', 'String', 0, 41);
  });
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
    variables: [{name: 'foo', type: 'String'}],
    selectedVariableIdx: 0,
    operator: 'not in',
    values: ['value1', 'value2']
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'variable',
    data: {
      name: 'foo',
      type: 'String',
      operator: 'not in',
      values: ['value1', 'value2']
    }
  });

  it('should disable add filter button if no value is selected', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: []
    });

    const buttons = node.find('#modal_actions button');
    expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
    expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
  });
});
