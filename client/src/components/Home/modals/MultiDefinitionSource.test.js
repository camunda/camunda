/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import MultiDefinitionSource from './MultiDefinitionSource';

const props = {
  onChange: jest.fn(),
  setInvalid: jest.fn(),
  definitions: [
    {
      key: 'hiring-demo',
      name: 'Hiring Demo',
      type: 'process',
      tenants: [
        {
          id: null,
          name: 'Not defined',
        },
      ],
    },
    {
      key: 'invoiceClassification',
      name: 'Invoice Classification',
      type: 'decision',
      tenants: [
        {
          id: null,
          name: 'Not defined',
        },
      ],
    },
  ],
};

it('should match snapshot', () => {
  const node = shallow(<MultiDefinitionSource {...props} />);

  expect(node).toMatchSnapshot();
});

it('should invoke onChange with the selected source', () => {
  const node = shallow(<MultiDefinitionSource {...props} />);

  node
    .find('Checklist')
    .props()
    .onChange([{key: 'invoiceClassification', type: 'decision'}]);

  expect(props.onChange).toHaveBeenCalledWith([
    {
      definitionKey: 'invoiceClassification',
      definitionType: 'decision',
      tenants: [null],
    },
  ]);
});
