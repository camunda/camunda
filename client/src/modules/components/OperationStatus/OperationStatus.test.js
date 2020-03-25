/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_STATE} from 'modules/constants';

import OperationStatus from './OperationStatus';

const actionSpinnerSelector = '[data-test="action-spinner"]';

describe('OperationStatus', () => {
  it('should render a spinner for scheduled operation', () => {
    // when
    const node = shallow(
      <OperationStatus
        operationState={OPERATION_STATE.SCHEDULED}
        instance={{id: 'foo'}}
      />
    );

    //then
    expect(node.find(actionSpinnerSelector)).toExist();
  });

  it('should render a spinner for locked operation', () => {
    // when
    const node = shallow(
      <OperationStatus
        operationState={OPERATION_STATE.LOCKED}
        instance={{id: 'foo'}}
      />
    );

    //then
    expect(node.find(actionSpinnerSelector)).toExist();
  });

  it('should render a spinner for sent operation', () => {
    // when
    const node = shallow(
      <OperationStatus
        operationState={OPERATION_STATE.SENT}
        instance={{id: 'foo'}}
      />
    );

    //then
    expect(node.find(actionSpinnerSelector)).toExist();
  });

  it('should render a spinner if forceSpinner prop is true', () => {
    // when
    const node = shallow(
      <OperationStatus
        operationState={OPERATION_STATE.CANCELED}
        instance={{id: 'foo'}}
        forceSpinner
      />
    );

    //then
    expect(node.find(actionSpinnerSelector)).toExist();
  });

  it('should not render a spinner', () => {
    // when
    const node = shallow(
      <OperationStatus
        operationState={OPERATION_STATE.CANCELED}
        instance={{id: 'foo'}}
      />
    );

    //then
    expect(node.find(actionSpinnerSelector)).not.toExist();
  });
});
