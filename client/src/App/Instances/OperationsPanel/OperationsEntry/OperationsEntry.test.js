/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATIONS} from './OperationsEntry.setup';

import OperationsEntry from './OperationsEntry';
import * as Styled from './styled.js';

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    // when
    const node = shallow(
      <OperationsEntry
        id={OPERATIONS.RETRY.id}
        type={OPERATIONS.RETRY.type}
        isRunning={true}
        endDate={OPERATIONS.RETRY.endDate}
      />
    );

    // then
    const endDateNode = node.find(Styled.EndDate);
    expect(endDateNode).not.toExist();

    const html = node.html();
    expect(html).toContain(OPERATIONS.RETRY.id);
    expect(html).toContain('Retry');
    expect(node.find('[data-test="operation-icon"]').length).toBe(1);
  });

  it('should render cancel operation', () => {
    // when
    const node = shallow(
      <OperationsEntry
        id={OPERATIONS.CANCEL.id}
        type={OPERATIONS.CANCEL.type}
        isRunning={true}
        endDate={OPERATIONS.CANCEL.endDate}
      />
    );

    // then
    const endDateNode = node.find(Styled.EndDate);
    expect(endDateNode).toExist();
    expect(endDateNode.text()).toContain('12 Dec 2018 00:00:00');

    const html = node.html();
    expect(html).toContain(OPERATIONS.CANCEL.id);
    expect(html).toContain('Cancel');
    expect(node.find('[data-test="operation-icon"]').length).toBe(1);
  });

  it('should render edit operation', () => {
    // when
    const node = shallow(
      <OperationsEntry
        id={OPERATIONS.EDIT.id}
        type={OPERATIONS.EDIT.type}
        isRunning={false}
        endDate={OPERATIONS.EDIT.endDate}
      />
    );

    // then

    const endDateNode = node.find(Styled.EndDate);
    expect(endDateNode).toExist();
    expect(endDateNode.text()).toEqual('12 Dec 2018 00:00:00');

    const html = node.html();
    expect(html).toContain(OPERATIONS.EDIT.id);
    expect(html).toContain('Edit');
    expect(node.find('[data-test="operation-icon"]').length).toBe(1);
  });
});
