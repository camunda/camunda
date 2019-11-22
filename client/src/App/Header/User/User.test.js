/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import User from './User';
import * as Styled from './styled.js';

const user = {
  firstname: 'Jonny',
  lastname: 'Prosciutto',
  canLogout: true
};

describe('Userarea', () => {
  it('should show Skeleton till data is available', () => {
    const mockProps = {
      firstname: null,
      lastname: null,
      canLogout: user.canLogout,
      handleLangout: jest.fn()
    };
    const node = mount(
      <ThemeProvider>
        <User {...mockProps} />
      </ThemeProvider>
    );

    expect(node.find(Styled.SkeletonBlock)).toExist();
    expect(node.find(Styled.Circle)).toExist();
  });

  it('should show user data when available', () => {
    const mockProps = {
      firstname: user.firstname,
      lastname: user.lastname,
      canLogout: user.canLogout,
      handleLangout: jest.fn()
    };
    const node = mount(
      <ThemeProvider>
        <User {...mockProps} />
      </ThemeProvider>
    );
    const DropdownLabel = node.find('Dropdown').prop('label');
    expect(DropdownLabel).toContain(user.firstname);
    expect(DropdownLabel).toContain(user.lastname);
  });
});
