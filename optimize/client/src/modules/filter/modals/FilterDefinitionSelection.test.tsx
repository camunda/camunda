/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {FilterableMultiSelect} from '@carbon/react';

import FilterDefinitionSelection from './FilterDefinitionSelection';

const props = {
  availableDefinitions: [
    {
      identifier: 'def1',
      displayName: 'Definition 1',
    },
    {
      identifier: 'def2',
      displayName: 'Definition 2',
    },
  ],
  applyTo: [],
  setApplyTo: jest.fn(),
};

beforeEach(() => {
  props.setApplyTo.mockClear();
});

it('should show available definitions', () => {
  const node = shallow(<FilterDefinitionSelection {...props} />);

  const listItems = node.find(FilterableMultiSelect).prop('items');
  expect(listItems).toEqual([
    {id: 'def1', label: 'Definition 1'},
    {id: 'def2', label: 'Definition 2'},
  ]);
});

it('should invoke setApplyTo when selecting a definition', () => {
  const node = shallow(<FilterDefinitionSelection {...props} />);

  const listItems = node.find(FilterableMultiSelect).prop('items');
  node.find(FilterableMultiSelect).simulate('change', {selectedItems: [listItems[1]]});

  expect(props.setApplyTo).toHaveBeenCalledWith([
    {displayName: 'Definition 2', identifier: 'def2'},
  ]);
});

it('should select all definitions when receiving the all identifier', () => {
  const node = shallow(
    <FilterDefinitionSelection
      {...props}
      applyTo={[{identifier: 'all', displayName: 'All included processes'}]}
    />
  );

  const listItems = node.find(FilterableMultiSelect).prop('initialSelectedItems');
  expect(listItems).toHaveLength(2);
});
