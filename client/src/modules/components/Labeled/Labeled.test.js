/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Labeled from './Labeled';

it('should pass props to label child element', () => {
  const node = shallow(
    <Labeled id="someId">
      <div className="test" />
    </Labeled>
  );

  expect(node.find('label')).toHaveProp('id', 'someId');
});
it('should include the child content', () => {
  const node = shallow(
    <Labeled id="someId">
      <div>some child content</div>
      <div>test</div>
    </Labeled>
  );

  expect(node).toIncludeText('some child content');
});

it('should can be disabled', () => {
  const node = shallow(<Labeled id="someId" disabled />);

  expect(node).toHaveProp('disabled', true);
});
