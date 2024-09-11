/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEvent} from 'react';
import {shallow} from 'enzyme';
import {runLastEffect} from '__mocks__/react';

import {loadVariables} from 'services';
import {Table} from 'components';
import {ProcessFilter} from 'types';

import {updateVariables} from './service';
import RenameVariablesModal from './RenameVariablesModal';

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

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
  open: true,
  availableTenants: [null, 'engineering'],
  definitionKey: '',
  onChange: jest.fn(),
  onClose: jest.fn(),
};

it('should load all variables when opening the modal for the specified definition', () => {
  const processDefinitionKey = '123';
  const node = shallow(
    <RenameVariablesModal {...props} open={false} definitionKey={processDefinitionKey} />
  );

  runLastEffect();

  expect(loadVariables).not.toHaveBeenCalled();

  node.setProps({open: true});
  runLastEffect();

  expect(node.find(Table).prop<JSX.Element[][]>('body')[0]?.[2]?.props.value).toBe('existingLabel');
  expect(loadVariables).toHaveBeenCalledWith({
    processesToQuery: [
      {processDefinitionKey, processDefinitionVersions: ['all'], tenantIds: props.availableTenants},
    ],
    filter: [],
  });
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
    .prop<JSX.Element[][]>('body')[0]?.[2]
    ?.props.onChange({target: {value: 'new name'}});

  node.find('.confirm').simulate('click');

  expect(node.find(Table).prop<JSX.Element[][]>('body')[0]?.[2]?.props.value).toBe('new name');
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

  const toolbar = shallow(node.find(Table).prop<JSX.Element>('toolbar'));
  toolbar.find('TableToolbarSearch').prop('onChange')?.({
    target: {value: 'variable1'},
  } as ChangeEvent<HTMLInputElement>);

  const variables = node.find(Table).prop('body') as string[][];
  expect(variables.length).toBe(1);
  expect(variables[0]?.[0]).toBe('variable1');
});

it('should pass filters to loadVariables', () => {
  const filters = [{appliedTo: ['all'], filterLevel: 'instance', type: 'assignee', data: []}];
  shallow(
    <RenameVariablesModal
      {...props}
      filters={[
        {appliedTo: ['all'], filterLevel: 'instance', type: 'assignee', data: []} as ProcessFilter,
      ]}
    />
  );

  runLastEffect();

  expect(loadVariables).toHaveBeenCalledWith({
    processesToQuery: [
      {
        processDefinitionKey: '',
        processDefinitionVersions: ['all'],
        tenantIds: props.availableTenants,
      },
    ],
    filter: filters,
  });
});
