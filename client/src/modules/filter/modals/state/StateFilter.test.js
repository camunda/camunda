/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import StateFilter from './StateFilter';

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

it('should call the addFilter prop with the selected filter option', () => {
  const spy = jest.fn();

  const node = shallow(<StateFilter addFilter={spy} />);

  node.find({label: 'Filter Option 2'}).simulate('change');
  node.find({primary: true}).simulate('click');

  expect(spy).toHaveBeenCalledWith({type: 'option2', appliedTo: ['all']});
});
