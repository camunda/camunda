/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import FilterSingleDefinitionSelection from './FilterSingleDefinitionSelection';

const props = {
  availableDefinitions: [
    {
      identifier: '1',
      key: '1',
      versions: ['all'],
      tenantIds: [null],
      displayName: 'valid definition',
    },
    {
      identifier: '2',
      key: '2',
      versions: ['all'],
      tenantIds: [],
      displayName: 'missing Tenant',
    },
  ],
  applyTo: undefined,
  setApplyTo: () => {},
};

it('should show only definitions that have version and tenant', () => {
  const node = shallow(<FilterSingleDefinitionSelection {...props} />);

  const options = node.find('ComboBox').prop('items') as unknown[];
  const itemToString = node.find('ComboBox').prop('itemToString') as (item: unknown) => string;
  expect(options.length).toBe(1);
  expect(itemToString(options[0])).toContain('valid definition');
  expect(itemToString(options[0])).not.toContain('missing Tenant');
});
