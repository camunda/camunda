/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import LogoutAwareUserMenu from './LogoutAwareUserMenu';

const props = {
  userName: 'userName',
  userEmail: 'user@example.com',
  items: [{key: 'imprint', label: 'Imprint', onClick: jest.fn()}],
  onLogout: jest.fn(),
  customSection: <div className="customSection" />,
  ariaLabel: 'settings',
};

it('should offer logging out when the deployment supports it', () => {
  const node = shallow(<LogoutAwareUserMenu {...props} canLogout />);

  expect(node.find('UserMenu').prop('onLogout')).toBe(props.onLogout);
});

it('should render a menu without a logout entry when logging out is hidden', () => {
  const node = shallow(<LogoutAwareUserMenu {...props} canLogout={false} />);

  expect(node.find('UserMenu')).toHaveLength(0);
  expect(node.find('DropdownMenuItem')).toHaveLength(props.items.length);
  expect(node.find('.customSection')).toExist();
});
