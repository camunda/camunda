/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import StateFilter from './StateFilter';
import {ComponentProps} from 'react';

jest.mock('./options', () => () => ({
  modalTitle: 'State Filter',
  pretext: 'Prefilter text',
  mappings: [
    {
      key: 'option1',
      label: 'Filter Option 1',
    },
    {
      key: 'option2',
      label: 'Filter Option 2',
    },
  ],
}));

const props: ComponentProps<typeof StateFilter> = {
  close: jest.fn(),
  definitions: [],
  filterType: 'runningInstancesOnly',
  filterLevel: 'instance',
  addFilter: jest.fn(),
};

it('should call the addFilter prop with the selected filter option', () => {
  const spy = jest.fn();

  const node = shallow(<StateFilter {...props} addFilter={spy} />);

  node.find({labelText: 'Filter Option 2'}).simulate('click');
  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({type: 'option2', appliedTo: ['all']});
});
