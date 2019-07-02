/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import StringInput from './StringInput';

import {shallow} from 'enzyme';

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
  const node = shallow(<StringInput {...props} />);

  expect(node.find('TypeaheadMultipleSelection')).toExist();
});

it('should load 10 values initially', () => {
  shallow(<StringInput {...props} />);

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 11, '');
});

it('should pass available values to the typeahead', () => {
  const availableValues = ['value1', 'value2', 'value3'];
  const node = shallow(<StringInput {...props} />);
  node.setState({
    availableValues
  });

  expect(node.find('TypeaheadMultipleSelection').props().availableValues).toEqual(availableValues);
});

it('should load 10 more values if the user wants more', () => {
  const node = shallow(<StringInput {...props} />);
  node.setState({
    availableValues: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    valuesAreComplete: false,
    valuesLoaded: 10,
    loading: false
  });

  node.find('.StringInput__load-more-button').simulate('click', {preventDefault: jest.fn()});

  expect(props.config.getValues).toHaveBeenCalledWith('foo', 'String', 21, '');
});

it('should disable add filter button if no value is selected', () => {
  const changeSpy = jest.fn();
  const validSpy = jest.fn();
  const node = shallow(
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
