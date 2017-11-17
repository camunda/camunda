import React from 'react';

import VariableFilter from './VariableFilter';
import {loadVariables, loadValues} from './service';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id='modal'>{props.children}</div>;
  Modal.Header = props => <div id='modal_header'>{props.children}</div>;
  Modal.Content = props => <div id='modal_content'>{props.children}</div>;
  Modal.Actions = props => <div id='modal_actions'>{props.children}</div>;

  const Select = props => <select {...props} id='select'>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
  Modal,
  Select,
  Button: props => <button {...props}>{props.children}</button>,
  Input: props => <input {...props}/>
}});

jest.mock('./service', () => {
  const variables = [
    {name: 'boolVar', type: 'Boolean'},
    {name: 'numberVar', type: 'Float'},
    {name: 'stringVar', type: 'String'},
  ]

  return {
    loadVariables: jest.fn().mockReturnValue(variables),
    loadValues: jest.fn().mockReturnValue(['val1', 'val2'])
  }
});

it('should contain a modal', () => {
  const node = mount(<VariableFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should initially load available variables', () => {
  mount(<VariableFilter processDefinitionId='procDefId' />);

  expect(loadVariables).toHaveBeenCalledWith('procDefId');
});

it('should display available variables', () => {
  const node = mount(<VariableFilter processDefinitionId='procDefId' />);

  node.setState({
    variables: [{name: 'foo', type: 'String'}, {name: 'bar', type: 'String'}]
  });

  expect(node.find('option').at(1)).toIncludeText('foo');
  expect(node.find('option').at(2)).toIncludeText('bar');
});

describe('boolean variables', () => {
  it('should assume variable value true per default', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(node.state().values).toEqual([true]);
  });

  it('should show true and false operator fields', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}],
      selectedVariableIdx: 0,
    });

    expect(node.find('button').at(0)).toIncludeText('true');
    expect(node.find('button').at(1)).toIncludeText('false');
  });

  it('should set the value when clicking on the operator fields', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Boolean'}],
      selectedVariableIdx: 0
    });

    node.find('button').at(1).simulate('click');

    expect(node.state().values).toEqual([false]);
  });
});

describe('number variables', () => {
  it('should be initialized with an empty variable value', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Float'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(node.state().values).toEqual(['']);
  });

  it('should store the input in the state value array at the correct position', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0', 'value1', 'value2']
    });

    node.find('.VariableFilter__valueFields input').at(1).simulate('change', {target: {getAttribute: jest.fn().mockReturnValue(1), value: 'newValue'}});

    expect(node.state().values).toEqual(['value0', 'newValue', 'value2']);
  });

  it('should display the possibility to add another value', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0']
    });

    expect(node).toIncludeText('Add Another Value');
  });

  it('should not display the possibility to add another value if last field is empty', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0', 'value1', '']
    });

    expect(node).not.toIncludeText('Add Another Value');
  });

  it('should add another value when clicking add another value button', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'Float'}],
      selectedVariableIdx: 0,
      values: ['value0', 'value1']
    });

    node.find('.VariableFilter__valueFields button').simulate('click');

    expect(node.state().values).toEqual(['value0', 'value1', '']);
  });
});

describe('string variables', () => {
  it('should load 20 values initially', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'String'}]
    });

    node.find('select').simulate('change', {target: {value: '0'}});

    expect(loadValues).toHaveBeenCalledWith('procDefId', 'foo', 'String', 0, 20);
  });

  it('should show available values', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
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
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: ['value1', 'value2', 'value3']
    });

    node.find('input[type="checkbox"]').at(1).simulate('change', {target: {checked: true, value: 'value2'}});

    expect(node.state().values).toEqual(['value2']);
  });

  it('should load 20 more values if the user wants more', () => {
    const node = mount(<VariableFilter processDefinitionId='procDefId' />);
    node.setState({
      variables: [{name: 'foo', type: 'String'}],
      selectedVariableIdx: 0,
      availableValues: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20],
      valuesAreComplete: false
    });

    node.find('.VariableFilter__valueFields button').simulate('click');

    expect(loadValues).toHaveBeenCalledWith('procDefId', 'foo', 'String', 0, 40);
  });
});

it('should create a new filter', () => {
  const spy = jest.fn();
  const node = mount(<VariableFilter processDefinitionId='procDefId' addFilter={spy} />);

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
});
