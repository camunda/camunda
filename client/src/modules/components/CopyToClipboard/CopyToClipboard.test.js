/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import CopyToClipboard from './CopyToClipboard';

it('should render without crashing', () => {
  mount(<CopyToClipboard />);
});

it('should set a value to its Input as provided as a prop', () => {
  const val = '123';
  const node = mount(<CopyToClipboard value={val} />);

  expect(node.find('input').at(0)).toHaveValue(val);
});

// re-enable this test once https://github.com/airbnb/enzyme/issues/1604 is fixed
// it('should copy the value of the input field to the clipboard on clicking the "Copy" button', () => {
//   const node = mount(<CopyToClipboard />);

//   node.find('button').simulate('click');
//   expect(document.execCommand).toHaveBeenCalledWith('Copy');
// });
