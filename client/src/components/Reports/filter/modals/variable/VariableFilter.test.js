import React from 'react';

import VariableFilter from './VariableFilter';
import {loadVariables, loadValues} from './service';

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

jest.mock('debounce', () => foo => foo);

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
    values: ['bar'],
    selectedVariable: {type: 'String', name: 'StrVar'}
  });
  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeFalsy(); // create filter
});

describe('boolean variables', () => {
  it('should assume variable value true per default', async () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    await node.instance().selectVariable({type: 'Boolean', name: 'BoolVar'});

    expect(node.state().values).toEqual([true]);
  });

  it('should show true and false operator fields', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'Boolean'}
    });

    expect(node.find('button').at(0)).toIncludeText('true');
    expect(node.find('button').at(1)).toIncludeText('false');
  });

  it('should set the value when clicking on the operator fields', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'Boolean'}
    });

    node
      .find('button')
      .at(1)
      .simulate('click');

    expect(node.state().values).toEqual([false]);
  });
});

describe('number variables', () => {
  it('should be initialized with an empty variable value', async () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    await node.instance().selectVariable({name: 'foo', type: 'Float'});

    expect(node.state().values).toEqual(['']);
  });

  it('should store the input in the state value array at the correct position', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'Float'},
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
      selectedVariable: {name: 'foo', type: 'Float'},
      values: ['value0']
    });

    expect(node.find('.VariableFilter__addValueButton')).toBePresent();
  });

  it('should add another value when clicking add another value button', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'Float'},
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
      selectedVariable: {name: 'foo', type: 'Float'},
      values: ['value0']
    });

    expect(node.find('.VariableFilter__removeItemButton').exists()).toBeFalsy();
  });

  it('should have the possibility to remove a value if there are multiple values', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'Float'},
      values: ['value0', 'value1']
    });

    expect(node.find('.VariableFilter__removeItemButton button').length).toBe(2);
  });

  it('should remove all values except the first one and the "add value" button if operator is "is less/greater than"', async () => {
    const node = await mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    await node.setState({
      selectVariable: {name: 'foo', type: 'Float'},
      values: ['123', '12', '17']
    });

    await node.instance().selectOperator({
      preventDefault: () => null,
      target: {
        getAttribute: atr => (atr === 'value' ? null : '<')
      }
    });
    await node.update();
    expect(node.state().values).toHaveLength(1);
    expect(node.find('.VariableFilter__addValueButton')).not.toBePresent();
  });

  it('should disable add filter button if provided value is invalid', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      selectedVariable: {name: 'foo', type: 'Float'},
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
      selectedVariable: {name: 'foo', type: 'Float'},
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
      selectedVariable: {name: 'foo', type: 'Integer'},
      values: ['123.23']
    });

    const buttons = node.find('#modal_actions button');
    expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
    expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
  });
});

describe('string variables', () => {
  it('should load 10 values initially', async () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    await node.instance().selectVariable({name: 'foo', type: 'String'});

    expect(loadValues).toHaveBeenCalledWith('procDefKey', '1', 'foo', 'String', 0, 11, '');
  });

  it('should show available values', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'String'},
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
      selectedVariable: {name: 'foo', type: 'String'},
      availableValues: ['value1', 'value2', 'value3']
    });

    node
      .find('input[type="checkbox"]')
      .at(1)
      .simulate('change', {target: {checked: true, value: 'value2'}});

    expect(node.state().values).toEqual(['value2']);
  });

  it('should load 10 more values if the user wants more', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );
    node.setState({
      selectedVariable: {name: 'foo', type: 'String'},
      availableValues: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
      valuesAreComplete: false
    });

    node.find('.VariableFilter__valueFields button').simulate('click');

    expect(loadValues).toHaveBeenCalledWith('procDefKey', '1', 'foo', 'String', 0, 21, '');
  });

  it('should request the values filtered by prefix entered in the input', () => {
    const node = mount(
      <VariableFilter processDefinitionKey="procDefKey" processDefinitionVersion="1" />
    );

    node.setState({
      selectedVariable: {name: 'foo', type: 'String'}
    });

    node
      .find('.VariableFilter__string-value-input')
      .first()
      .simulate('change', {target: {value: 'eeeee'}});

    expect(loadValues).toHaveBeenCalledWith('procDefKey', '1', 'foo', 'String', 0, 11, 'eeeee');
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
    selectedVariable: {name: 'foo', type: 'String'},
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
      selectedVariable: {name: 'foo', type: 'String'},
      availableValues: []
    });

    const buttons = node.find('#modal_actions button');
    expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
    expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
  });
});
