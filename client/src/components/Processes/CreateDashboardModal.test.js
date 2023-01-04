/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {Button} from 'components';

import CreateDashboardModal from './CreateDashboardModal';
import {Link} from 'react-router-dom';

const props = {
  linkToDashboard: 'https://www.example.com',
};

it('should render modal properly', () => {
  const node = shallow(<CreateDashboardModal {...props} />);

  expect(node).toBeDefined();
});

it('should call onClose when clicking on cancel button', () => {
  const spy = jest.fn();
  const node = shallow(<CreateDashboardModal {...props} onClose={spy} />);

  node.find(Button).simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should pass onClose to modal component', () => {
  const spy = jest.fn();
  const node = shallow(<CreateDashboardModal {...props} onClose={spy} />);

  node.prop('onClose')();
  expect(spy).toHaveBeenCalled();
});

it('should call onClose when clicking on cancel button', () => {
  const spy = jest.fn();
  const node = shallow(<CreateDashboardModal {...props} onConfirm={spy} />);

  const link = node.find(Link);
  link.simulate('click');

  expect(link.prop('to')).toBe('https://www.example.com');
  expect(spy).toHaveBeenCalled();
});
