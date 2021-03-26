/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import OperationItems from './index';
import {OPERATION_TYPE} from 'modules/constants';

import * as Styled from './styled';

describe('OperationItems', () => {
  let node: any;
  let mockOnClick = jest.fn();

  beforeEach(() => {
    node = shallow(
      <OperationItems>
        <OperationItems.Item
          type={OPERATION_TYPE.RESOLVE_INCIDENT}
          onClick={() => mockOnClick()}
        />
      </OperationItems>
    );
  });

  it('should render with its children', () => {
    expect(node.find(Styled.Ul)).toExist();
    expect(node.find(OperationItems.Item)).toExist();
  });

  describe('Retry Item', () => {
    beforeEach(() => {
      node = shallow(
        <OperationItems.Item
          type={OPERATION_TYPE.RESOLVE_INCIDENT}
          onClick={() => mockOnClick()}
          title={'Retry Instance 1'}
        />
      );
    });

    it('should show the correct icon based on the type', () => {
      expect(node.find(Styled.Button).props().type).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
      expect(node.find(Styled.RetryIcon)).toExist();
    });

    it('should display title', () => {
      expect(node.find(Styled.Button).props().title).toBe('Retry Instance 1');
    });

    it('should execute the passed method when clicked', () => {
      node.find(Styled.Li).simulate('click');
      expect(mockOnClick).toHaveBeenCalled();
    });
  });

  describe('Cancel Item', () => {
    beforeEach(() => {
      node = shallow(
        <OperationItems.Item
          type={OPERATION_TYPE.CANCEL_PROCESS_INSTANCE}
          onClick={() => mockOnClick()}
          title={'Cancel Instance 1'}
        />
      );
    });

    it('should show the correct icon based on the type', () => {
      expect(node.find(Styled.Button).props().type).toBe(
        OPERATION_TYPE.CANCEL_PROCESS_INSTANCE
      );
      expect(node.find(Styled.CancelIcon)).toExist();
    });

    it('should display title', () => {
      expect(node.find(Styled.Button).props().title).toBe('Cancel Instance 1');
    });

    it('should execute the passed method when clicked', () => {
      node.find(Styled.Li).simulate('click');
      expect(mockOnClick).toHaveBeenCalled();
    });
  });
});
