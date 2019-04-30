/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import StringInput from './StringInput';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const TypeaheadMultipleSelection = props => {
    const allowedProps = {...props};
    delete allowedProps.toggleValue;
    delete allowedProps.setFilter;
    delete allowedProps.availableValues;
    delete allowedProps.selectedValues;
    delete allowedProps.format;
    return <div {...allowedProps}>{props.availableValues.concat(props.selectedValues)}</div>;
  };

  return {
    TypeaheadMultipleSelection,
    Button: props => <button {...props}>{props.children}</button>,
    ButtonGroup: props => <div {...props}>{props.children}</div>,
    LoadingIndicator: () => <div className="sk-circle">Loading...</div>
  };
});

jest.mock('debounce', () => foo => foo);

const props = {
  processDefinitionKey: 'procDefKey',
  processDefinitionVersion: '1',
  variable: {name: 'foo', type: 'String'},
  filter: StringInput.defaultFilter,
  config: {getValues: jest.fn().mockReturnValue(['val1', 'val2'])},
  setValid: jest.fn()
};

it('should show a typeahead', () => {
  const node = mount(<StringInput {...props} />);

  expect(node.find('TypeaheadMultipleSelection')).toExist();
});

it('should load 10 values initially', () => {
  mount(<StringInput {...props} />);

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 11, '');
});

it('should show available values', () => {
  const node = mount(<StringInput {...props} />);
  node.setState({
    availableValues: ['value1', 'value2', 'value3']
  });

  expect(node.find('TypeaheadMultipleSelection')).toIncludeText('value1');
  expect(node.find('TypeaheadMultipleSelection')).toIncludeText('value2');
  expect(node.find('TypeaheadMultipleSelection')).toIncludeText('value3');
});

it('should load 10 more values if the user wants more', () => {
  const node = mount(<StringInput {...props} />);
  node.setState({
    availableValues: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    valuesAreComplete: false,
    valuesLoaded: 10,
    loading: false
  });
  node
    .find('.StringInput__load-more-button')
    .at(1)
    .simulate('click');

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 21, '');
});

it('should disable add filter button if no value is selected', () => {
  const changeSpy = jest.fn();
  const validSpy = jest.fn();
  const node = mount(
    <StringInput
      {...props}
      filter={{operator: 'in', values: ['A']}}
      changeFilter={changeSpy}
      setValid={validSpy}
    />
  );

  node.instance().toggleValue('A', false);

  expect(changeSpy).toHaveBeenCalledWith({operator: 'in', values: []});
  expect(validSpy).toHaveBeenCalledWith(false);
});
