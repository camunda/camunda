/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATIONS, mockProps} from './OperationsEntry.setup';

import OperationsEntry from './OperationsEntry';
import * as Styled from './styled.js';

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    // when

    const node = shallow(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.RETRY,
          instancesCount: 1,
        }}
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
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.CANCEL,
          instancesCount: 1,
        }}
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
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
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

  it('should render instances count with when there is one instance', () => {
    // when

    const node = shallow(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />
    );

    // then

    const instancesCountNode = node.find(Styled.InstancesCount);
    expect(instancesCountNode).toExist();
    expect(instancesCountNode.text()).toEqual('1 Instance');
  });

  it('should render instances count with when there is more than one instance', () => {
    // when

    const node = shallow(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />
    );

    // then

    const instancesCountNode = node.find(Styled.InstancesCount);
    expect(instancesCountNode).toExist();
    expect(instancesCountNode.text()).toEqual('3 Instances');
  });

  it('should be able to handle instance click', () => {
    // given

    const node = shallow(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />
    );

    // when
    const InstancesCountNode = node.find(Styled.InstancesCount);

    InstancesCountNode.simulate('click');

    // then
    expect(mockProps.onInstancesClick).toHaveBeenCalledWith(
      'df325d44-6a4c-4428-b017-24f923f1d052'
    );
  });
});
