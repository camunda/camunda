/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Typeahead} from 'components';

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

  const options = node.find(Typeahead.Option);
  expect(options.length).toBe(1);
  expect(options).toIncludeText('valid definition');
  expect(options).not.toIncludeText('missing Tenant');
});
