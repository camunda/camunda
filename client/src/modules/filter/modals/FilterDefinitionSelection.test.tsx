/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {MultiSelect} from 'components';

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

  expect(node.find(MultiSelect.Option)).toHaveLength(3);
  expect(node.find(MultiSelect.Option).at(1)).toIncludeText('Definition 1');
  expect(node.find(MultiSelect.Option).at(2)).toIncludeText('Definition 2');
});

it('should allow selecting special all definition', () => {
  const node = shallow(<FilterDefinitionSelection {...props} />);

  const specialOption = node.find(MultiSelect.Option).first();

  expect(specialOption).toIncludeText('All included processes');

  node.find(MultiSelect).simulate('add', specialOption.prop('value'));

  expect(props.setApplyTo).toHaveBeenCalledWith([specialOption.prop('value')]);
});

it('should allow selecting special all definition', () => {
  const node = shallow(
    <FilterDefinitionSelection {...props} applyTo={props.availableDefinitions} />
  );

  const specialOption = node.find(MultiSelect.Option).first();

  node.find(MultiSelect).simulate('add', specialOption.prop('value'));
  expect(props.setApplyTo).toHaveBeenCalledWith([specialOption.prop('value')]);
});

it('should not show any available definition when special all definition is selected', () => {
  const node = shallow(
    <FilterDefinitionSelection
      {...props}
      applyTo={[{identifier: 'all', displayName: 'All included processes'}]}
    />
  );

  expect(node.find(MultiSelect.Option)).toHaveLength(0);
});
