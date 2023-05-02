/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import VisibleEventsModal from './VisibleEventsModal';
import {LabeledInput} from 'components';

const props = {
  initialScope: ['process_instance'],
  onConfirm: jest.fn(),
  onClose: jest.fn(),
};

beforeEach(() => {
  props.onConfirm.mockClear();
});

it('should match snapshot', () => {
  const node = shallow(<VisibleEventsModal {...props} />);

  expect(node.find('Form')).toMatchSnapshot();
});

it('should add a scope', () => {
  const node = shallow(<VisibleEventsModal {...props} />);

  node.find(LabeledInput).at(1).simulate('change');

  node.find('Button').at(1).simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith(['process_instance', 'start_end']);
});

it('should disable the confirm button when no scope is selected', () => {
  const node = shallow(<VisibleEventsModal {...props} />);

  node.find(LabeledInput).at(0).simulate('change');

  expect(node.find('Button').at(1)).toBeDisabled();
});
