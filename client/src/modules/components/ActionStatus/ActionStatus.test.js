/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import ActionStatus from './ActionStatus';
import StatusItems from './StatusItems';

import * as Styled from './styled';

describe('ActionStatus', () => {
  describe('ActionSpinner', () => {
    let node;

    it('should render a spinner', () => {
      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.SCHEDULED}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.ActionSpinner)).toExist();

      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.LOCKED}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.ActionSpinner)).toExist();

      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.SENT}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.ActionSpinner)).toExist();
    });
  });

  describe('FailedActionItems', () => {
    let node;

    it('should render a failed retry action icon', () => {
      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.FAILED}
          operationType={OPERATION_TYPE.RESOLVE_INCIDENT}
          instance={{id: 'foo'}}
        />
      );

      expect(node.find(StatusItems)).toExist();
      expect(node.find(StatusItems.Item)).toExist();
      expect(node.find(StatusItems.Item).props().type).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
    });

    it('should render a failed cancel action icon', () => {
      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.FAILED}
          operationType={OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE}
          instance={{id: 'foo'}}
        />
      );

      // then
      expect(node.find(StatusItems)).toExist();
      expect(node.find(StatusItems.Item)).toExist();
      expect(node.find(StatusItems.Item).props().type).toBe(
        OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
      );
    });
  });
});
