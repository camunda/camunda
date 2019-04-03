/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Labeled from './Labeled';
it('should pass props to label child element', () => {
  const node = mount(
    <Labeled id="someId">
      <div className="test" />
    </Labeled>
  );

  expect(node.find('label')).toHaveProp('id', 'someId');
});
it('should include the child content', () => {
  const node = mount(
    <Labeled id="someId">
      <div>some child content</div>
      <div>test</div>
    </Labeled>
  );

  expect(node).toIncludeText('some child content');
});
