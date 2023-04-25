/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {loadVariables} from 'services';
import {Table} from 'components';

import {updateVariables} from './service';
import {RenameVariablesModal} from './RenameVariablesModal';

jest.mock('./service', () => ({updateVariables: jest.fn()}));
jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([
      {name: 'variable1', type: 'String', label: 'existingLabel'},
      {name: 'variable2', type: 'String', label: null},
    ]),
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  availableTenants: [null, 'engineering'],
};

it('should load all variables for the specified definition', () => {
  const processDefinitionKey = '123';
  const node = shallow(<RenameVariablesModal {...props} definitionKey={processDefinitionKey} />);

  runLastEffect();

  expect(node.find(Table).prop('body')[0][2].props.value).toBe('existingLabel');
  expect(loadVariables).toHaveBeenCalledWith([
    {processDefinitionKey, processDefinitionVersions: ['all'], tenantIds: props.availableTenants},
  ]);
});

it('should invoke updateVariable when confirming the modal with the list of updated variables', () => {
  const changeSpy = jest.fn();
  const closeSpy = jest.fn();
  const definitionKey = '123';
  const node = shallow(
    <RenameVariablesModal
      {...props}
      definitionKey={definitionKey}
      onChange={changeSpy}
      onClose={closeSpy}
    />
  );

  runLastEffect();

  node
    .find(Table)
    .prop('body')[0][2]
    .props.onChange({target: {value: 'new name'}});

  node.find('.confirm').simulate('click');

  expect(node.find(Table).prop('body')[0][2].props.value).toBe('new name');
  expect(updateVariables).toHaveBeenCalledWith(definitionKey, [
    {variableLabel: 'new name', variableName: 'variable1', variableType: 'String'},
  ]);
  expect(changeSpy).toHaveBeenCalled();
  expect(closeSpy).toHaveBeenCalled();
});

it('should invoke onClose when closing the modal', () => {
  const spy = jest.fn();
  const node = shallow(<RenameVariablesModal {...props} onClose={spy} />);

  node.find('.cancel').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should filter items based on search', () => {
  const node = shallow(<RenameVariablesModal {...props} />);

  runLastEffect();

  node.find('.searchInput').simulate('change', {target: {value: 'variable1'}});

  const variables = node.find(Table).prop('body');
  expect(variables.length).toBe(1);
  expect(variables[0][0]).toBe('variable1');
});
