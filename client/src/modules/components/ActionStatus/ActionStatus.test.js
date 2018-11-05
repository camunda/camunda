import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_STATE, OPERATION_TYPE} from 'modules/constants';

import ActionStatus from './ActionStatus';
import StatusItems from '../StatusItems';

import * as Styled from './styled';

describe('ActionStatus', () => {
  describe('ActionSpinner', () => {
    let node;

    it('should render a spinner', () => {
      // when
      node = shallow(
        <ActionStatus operationState={OPERATION_STATE.SCHEDULED} />
      );
      //then
      expect(node.find(Styled.ActionSpinner)).toExist();

      // when
      node = shallow(<ActionStatus operationState={OPERATION_STATE.LOCKED} />);
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
          operationType={OPERATION_TYPE.UPDATE_RETRIES}
        />
      );

      expect(node.find(StatusItems)).toExist();
      expect(node.find(StatusItems.Item)).toExist();
      expect(node.find(StatusItems.Item).props().type).toBe(
        OPERATION_TYPE.UPDATE_RETRIES
      );
    });

    it('should render a failed cancel action icon', () => {
      // when
      node = shallow(
        <ActionStatus
          operationState={OPERATION_STATE.FAILED}
          operationType={OPERATION_TYPE.CANCEL}
        />
      );

      // then
      expect(node.find(StatusItems)).toExist();
      expect(node.find(StatusItems.Item)).toExist();
      expect(node.find(StatusItems.Item).props().type).toBe(
        OPERATION_TYPE.CANCEL
      );
    });
  });
});
