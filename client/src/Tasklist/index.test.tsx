/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, fireEvent, screen} from '@testing-library/react';

import {Tasklist} from './index';
import {login} from '../login.store';

jest.mock('../login.store');

describe('<Tasklist />', () => {
  it('should handle logout', () => {
    render(<Tasklist />);

    fireEvent.click(screen.getByRole('button', {name: 'logout'}));

    expect(login.handleLogout).toHaveBeenCalled();
  });
});
