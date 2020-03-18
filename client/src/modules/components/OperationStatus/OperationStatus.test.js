/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import OperationStatus from './OperationStatus';
import StatusItems from './StatusItems';

import * as Styled from './styled';

describe('OperationStatus', () => {
  describe('OperationSpinner', () => {
    let node;

    it('should render a spinner', () => {
      // when
      node = shallow(
        <OperationStatus
          operationState={OPERATION_STATE.SCHEDULED}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.OperationSpinner)).toExist();

      // when
      node = shallow(
        <OperationStatus
          operationState={OPERATION_STATE.LOCKED}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.OperationSpinner)).toExist();

      // when
      node = shallow(
        <OperationStatus
          operationState={OPERATION_STATE.SENT}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.OperationSpinner)).toExist();
    });
    it('should render a spinner if forceSpinner prop is true', () => {
      // when
      node = shallow(
        <OperationStatus
          operationState={OPERATION_STATE.CANCELED}
          instance={{id: 'foo'}}
          forceSpinner
        />
      );
      //then
      expect(node.find(Styled.OperationSpinner)).toExist();
    });
    it('should not render a spinner', () => {
      // when
      node = shallow(
        <OperationStatus
          operationState={OPERATION_STATE.CANCELED}
          instance={{id: 'foo'}}
        />
      );
      //then
      expect(node.find(Styled.OperationSpinner)).not.toExist();
    });
  });

  describe('FailedOperationItems', () => {
    let node;

    it('should render a failed retry operation icon', () => {
      // when
      node = shallow(
        <OperationStatus
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

    it('should render a failed cancel operation icon', () => {
      // when
      node = shallow(
        <OperationStatus
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
