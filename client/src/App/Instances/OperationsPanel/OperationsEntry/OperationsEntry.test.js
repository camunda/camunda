/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_TYPES} from '../constants';

import OperationsEntry from './OperationsEntry';

describe('OperationsEntry', () => {
  it('should render', () => {
    const node = shallow(
      <OperationsEntry
        id={123312}
        type={OPERATION_TYPES.RETRY}
        isRunning={true}
      />
    );

    const html = node.html();

    expect(html).toContain(123312);
    expect(html).toContain('Retry');
  });
});
