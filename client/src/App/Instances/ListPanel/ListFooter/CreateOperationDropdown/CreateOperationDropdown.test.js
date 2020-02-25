/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import CreateOperationDropdown from './';
import Dropdown from 'modules/components/Dropdown';

import React from 'react';
import {mount} from 'enzyme';

describe('CreateOperationDropdown', () => {
  it('should render button', () => {
    // given
    const node = mount(<CreateOperationDropdown label="MyLabel" />);

    // when
    const button = node.find('button');

    // then
    expect(button.text()).toContain('MyLabel');
  });

  it('should show dropdown menu on click', () => {
    // given
    const node = mount(<CreateOperationDropdown label="MyLabel" />);
    const button = node.find('[data-test="dropdown-toggle"]').first();

    // when
    button.simulate('click');

    // then
    const retryButton = node
      .find(Dropdown.Option)
      .at(0)
      .find('button');

    const cancelButton = node
      .find(Dropdown.Option)
      .at(1)
      .find('button');

    expect(retryButton.text()).toContain('Retry');
    expect(cancelButton.text()).toContain('Cancel');
  });
});
