/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import DefinitionEditor from './DefinitionEditor';
import {DefinitionList} from './DefinitionList';

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([
    {
      key: 'definitionA',
      versions: ['all'],
      tenants: [
        {id: 'a', name: 'Tenant A'},
        {id: 'b', name: 'Tenant B'},
        {id: 'c', name: 'Tenant C'},
      ],
    },
  ]),
}));

const props = {
  mightFail: (data, cb) => cb(data),
  location: '',
  type: 'process',
  definitions: [
    {
      key: 'definitionA',
      name: 'Definition A',
      displayName: 'Definition A',
      versions: ['all'],
      tenantIds: ['a', 'b'],
    },
  ],
};

it('should show a list of added definitions', () => {
  const node = shallow(<DefinitionList {...props} />);

  expect(node.find('li').length).toBe(1);
  expect(node.find(DefinitionEditor).prop('definition')).toEqual(props.definitions[0]);
});

it('should display names of tenants', () => {
  const node = shallow(<DefinitionList {...props} />);
  runLastEffect();

  expect(node.find('.info').at(1).text()).toBe('Tenant: Tenant A, Tenant B');
});

it('should allow copying definitions', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionList {...props} onCopy={spy} />);

  node.find(Button).first().simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should not allow copy if limit of 10 definitions is reached', () => {
  const node = shallow(
    <DefinitionList
      {...props}
      definitions={Array(10).fill({
        key: 'definitionA',
        name: 'Definition A',
        displayName: 'Definition A',
        versions: ['all'],
        tenantIds: ['a', 'b'],
      })}
    />
  );

  expect(node.find({type: 'copy-small'})).not.toExist();
});
