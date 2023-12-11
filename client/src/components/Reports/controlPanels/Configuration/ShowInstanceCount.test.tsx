/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import ShowInstanceCount from './ShowInstanceCount';

it('should contain a toggle that is checked if instance count is shown', () => {
  const node = shallow(
    <ShowInstanceCount label="instance" showInstanceCount onChange={jest.fn()} />
  );

  expect(node.find('Toggle').prop('toggled')).toBe(true);

  node.setProps({showInstanceCount: false});

  expect(node.find('Toggle').prop('toggled')).toBe(false);
});

it('should call the onChange method when toggling the switch', () => {
  const spy = jest.fn();

  const node = shallow(<ShowInstanceCount label="instance" showInstanceCount onChange={spy} />);

  node.find('Toggle').simulate('toggle', false);

  expect(spy).toHaveBeenCalledWith({showInstanceCount: {$set: false}});
});
