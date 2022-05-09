/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import {HelpMenu} from './HelpMenu';
import {isChangeLogSeen, setChangeLogAsSeen, getMarkdownText} from './service';

jest.mock('./service', () => ({
  isChangeLogSeen: jest.fn().mockReturnValue({seen: false}),
  setChangeLogAsSeen: jest.fn(),
  getMarkdownText: jest.fn().mockReturnValue('#hello world'),
}));

const props = {
  mightFail: (promise, cb) => cb(promise),
};

it('show match snapshot', () => {
  const node = shallow(<HelpMenu {...props} />);

  expect(node).toMatchSnapshot();
});

it('should not show the modal if it is seen before', () => {
  isChangeLogSeen.mockReturnValueOnce({seen: true});
  const node = shallow(<HelpMenu {...props} />);

  expect(node.find('Modal').props().open).toBe(false);
});

it('should load markdown when opening whats new modal', () => {
  isChangeLogSeen.mockReturnValueOnce({seen: true});
  const node = shallow(<HelpMenu {...props} />);

  node.find(Button).at(0).simulate('click');

  expect(getMarkdownText).toHaveBeenCalled();
});

it('should set status as seen when closing the whats new modal', () => {
  const node = shallow(<HelpMenu {...props} />);
  node.find('.close').simulate('click');

  expect(setChangeLogAsSeen).toHaveBeenCalled();
});
