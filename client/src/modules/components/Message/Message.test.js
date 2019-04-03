/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import Message from './Message';

it('renders without crashing', () => {
  shallow(<Message />);
});

it('renders the message text provided as a property', () => {
  const text = 'This is a Message!';

  const node = mount(<Message>{text}</Message>);
  expect(node).toIncludeText(text);
});

it('renders the class name as provided as a property', () => {
  const type = 'test';

  const node = mount(<Message type={type} />);
  expect(node.find('.Message')).toHaveClassName('Message--test');
});
