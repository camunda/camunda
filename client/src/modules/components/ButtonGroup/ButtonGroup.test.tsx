/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mount} from 'enzyme';

import ButtonGroup from './ButtonGroup';

it('should render without crashing', () => {
  mount(<ButtonGroup />);
});

it('should render its children', () => {
  const node = mount(
    <ButtonGroup>
      <button />
    </ButtonGroup>
  );

  expect(node.find('button')).toExist();
});

it('should apply passed classNames', () => {
  const node = mount(<ButtonGroup className="CustomClass" />);

  expect(node.find('.CustomClass')).toExist();
});

it('should also work when not providing classNames', () => {
  const node = mount(<ButtonGroup />);

  expect(node.find('.ButtonGroup')).toExist();
});
