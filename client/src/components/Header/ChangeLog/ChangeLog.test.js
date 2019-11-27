/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ChangeLogWithErrorHandling from './ChangeLog';
import {isChangeLogSeen, setChangeLogAsSeen, getMarkdownText} from './service';
import {Button} from 'components';

const {WrappedComponent: ChangeLog} = ChangeLogWithErrorHandling;

jest.mock('./service', () => ({
  isChangeLogSeen: jest.fn().mockReturnValue({seen: false}),
  setChangeLogAsSeen: jest.fn(),
  getMarkdownText: jest.fn().mockReturnValue('#hello world')
}));

const props = {
  mightFail: (promise, cb) => cb(promise)
};

it('show match snapshot', () => {
  const node = shallow(<ChangeLog {...props} />);

  expect(node).toMatchSnapshot();
});

it('should not show the modal if it is seen before', () => {
  isChangeLogSeen.mockReturnValueOnce({seen: true});
  const node = shallow(<ChangeLog {...props} />);

  expect(node.find('Modal').props().open).toBe(false);
});

it('should load markdown when opening modal', () => {
  isChangeLogSeen.mockReturnValueOnce({seen: true});
  const node = shallow(<ChangeLog {...props} />);

  node
    .find(Button)
    .at(0)
    .simulate('click');

  expect(getMarkdownText).toHaveBeenCalled();
});

it('should set status as seen when closing the modal', () => {
  const node = shallow(<ChangeLog {...props} />);
  node.find('.close').simulate('click');

  expect(setChangeLogAsSeen).toHaveBeenCalled();
});
