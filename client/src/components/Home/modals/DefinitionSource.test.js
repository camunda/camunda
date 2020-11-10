/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import DefinitionSource from './DefinitionSource';

const props = {
  onChange: jest.fn(),
  setInvalid: jest.fn(),
  definitionsWithTenants: [
    {
      key: 'hiring-demo',
      name: 'Hiring Demo',
      type: 'process',
      tenants: [
        {id: 'csm', name: 'csm'},
        {id: 'engineering', name: 'engineering'},
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
  const node = shallow(<DefinitionSource {...props} />);

  expect(node).toMatchSnapshot();
});

it('should invoke onChange with the selected source', () => {
  const node = shallow(<DefinitionSource {...props} />);

  node.find('Typeahead').props().onChange(props.definitionsWithTenants[0].key);

  node
    .find('Checklist')
    .props()
    .onChange([{id: 'csm', name: 'csm'}]);

  expect(props.onChange).toHaveBeenCalledWith([
    {
      definitionKey: 'hiring-demo',
      definitionType: 'process',
      tenants: ['csm'],
    },
  ]);
});

it('should preselect the only tenant and invoke onChange', () => {
  const node = shallow(<DefinitionSource {...props} />);

  node.find('Typeahead').props().onChange(props.definitionsWithTenants[1].key);

  expect(props.onChange).toHaveBeenCalledWith([
    {
      definitionKey: 'invoiceClassification',
      definitionType: 'decision',
      tenants: [null],
    },
  ]);
});
