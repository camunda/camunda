/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, fireEvent, screen} from '@testing-library/react';

import {Tasklist} from './index';
import {login} from 'modules/stores/login';

jest.mock('modules/stores/login');

describe('<Tasklist />', () => {
  it('should handle logout', () => {
    render(<Tasklist />);

    fireEvent.click(screen.getByRole('button', {name: 'Logout'}));

    expect(login.handleLogout).toHaveBeenCalled();
  });
});
