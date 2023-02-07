/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {WhatsNewModal} from './WhatsNewModal';
import {getMarkdownText, isChangeLogSeen, setChangeLogAsSeen} from './service';

jest.mock('./service', () => ({
  isChangeLogSeen: jest.fn().mockReturnValue({seen: false}),
  setChangeLogAsSeen: jest.fn(),
  getMarkdownText: jest.fn().mockReturnValue('#hello world'),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should show whats new text', () => {
  const node = shallow(<WhatsNewModal {...props} open />);

  runAllEffects();

  expect(getMarkdownText).toHaveBeenCalled();
  expect(node.find('reactMarkdown').childAt(0)).toHaveText('#hello world');
});

it('should show optimize version', async () => {
  const node = shallow(<WhatsNewModal {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.childAt(0).html()).toContain('2.7.0');
});

it('should not show the modal if it is seen before', () => {
  isChangeLogSeen.mockReturnValueOnce({seen: true});
  const node = shallow(<WhatsNewModal {...props} />);

  runAllEffects();

  expect(node.find('Modal').props().open).toBe(false);
});

it('should call onClose when closing modal', () => {
  const spy = jest.fn();
  const node = shallow(<WhatsNewModal {...props} onClose={spy} />);

  runAllEffects();

  node.find('Modal').prop('onClose')();

  expect(spy).toHaveBeenCalled();
});

it('should set status as seen when closing the whats new modal', () => {
  const node = shallow(<WhatsNewModal {...props} onClose={jest.fn()} />);

  runAllEffects();

  node.find('Modal').prop('onClose')();

  expect(setChangeLogAsSeen).toHaveBeenCalled();
});
