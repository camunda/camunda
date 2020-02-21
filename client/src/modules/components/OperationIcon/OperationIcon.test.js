/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import OperationIcon from './OperationIcon';
import {OPERATION_TYPE} from 'modules/constants';
import * as Styled from './styled';

describe('OperationIcon', () => {
  it('should render Edit icon if operation type is Edit', () => {
    // given
    const node = shallow(
      <OperationIcon operationType={OPERATION_TYPE.UPDATE_VARIABLE} />
    );

    // then
    expect(node.find(Styled.Edit)).toHaveLength(1);
  });

  it('should render Retry icon if operation type is Retry', () => {
    // given
    const node = shallow(
      <OperationIcon operationType={OPERATION_TYPE.RESOLVE_INCIDENT} />
    );

    // then
    expect(node.find(Styled.Retry)).toHaveLength(1);
  });

  it('should render Cancel icon if operation type is Cancel', () => {
    // given
    const node = shallow(
      <OperationIcon operationType={OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE} />
    );

    // then
    expect(node.find(Styled.Cancel)).toHaveLength(1);
  });
});
