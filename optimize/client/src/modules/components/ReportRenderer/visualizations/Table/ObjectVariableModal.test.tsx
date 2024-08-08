/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {loadObjectValues} from './service';
import {ObjectVariableModal} from './ObjectVariableModal';

jest.mock('./service', () => ({loadObjectValues: jest.fn().mockReturnValue({key: 'property'})}));

const props = {
  variable: {
    name: 'varName',
    processInstanceId: 'instanceId',
    processDefinitionKey: 'definitionKey',
    versions: ['1'],
    tenantIds: [null],
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load variable value string', () => {
  const node = shallow(<ObjectVariableModal {...props} />);

  runLastEffect();

  const {name, processInstanceId, processDefinitionKey, versions, tenantIds} = props.variable;
  expect(loadObjectValues).toHaveBeenCalledWith(
    name,
    processInstanceId,
    processDefinitionKey,
    versions,
    tenantIds
  );
  expect(node.find('pre').prop('children')).toEqual({key: 'property'});
});

it('should close the modal when clicking the close button', () => {
  const spy = jest.fn();
  const node = shallow(<ObjectVariableModal {...props} onClose={spy} />);

  runLastEffect();

  node.find('.close').simulate('click');

  expect(spy).toHaveBeenCalled();
});
