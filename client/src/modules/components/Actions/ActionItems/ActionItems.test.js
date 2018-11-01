import React from 'react';
import {shallow} from 'enzyme';

import ActionItems from './ActionItems';
import {OPERATION_TYPE} from 'modules/constants';

import * as Styled from './styled';

describe('ActionItems', () => {
  let node;
  let mockOnClick = jest.fn();

  beforeEach(() => {
    node = shallow(
      <ActionItems>
        <ActionItems.Item
          type={OPERATION_TYPE.UPDATE_RETRIES}
          onClick={() => mockOnClick()}
        />
      </ActionItems>
    );
  });

  it('should render with its children', () => {
    expect(node.find(Styled.Ul)).toExist();
    expect(node.find(ActionItems.Item)).toExist();
  });

  describe('Retry Item', () => {
    beforeEach(() => {
      node = shallow(
        <ActionItems.Item
          type={OPERATION_TYPE.UPDATE_RETRIES}
          onClick={() => mockOnClick()}
          title={'Retry instance 1'}
        />
      );
    });

    it('should show the correct icon based on the type', () => {
      expect(node.find(Styled.Button).props().type).toBe(
        OPERATION_TYPE.UPDATE_RETRIES
      );
      expect(node.find(Styled.RetryIcon)).toExist();
    });

    it('should use the input type as title', () => {
      expect(node.find(Styled.Button).props().title).toBe('Retry instance 1');
    });

    it('should execute the passed method when clicked', () => {
      node.find(Styled.Button).simulate('click');
      expect(mockOnClick).toHaveBeenCalled();
    });
  });

  describe('Cancel Item', () => {
    beforeEach(() => {
      node = shallow(
        <ActionItems.Item
          type={OPERATION_TYPE.CANCEL}
          onClick={() => mockOnClick()}
          title={'Cancel instance 1'}
        />
      );
    });

    it('should show the correct icon based on the type', () => {
      expect(node.find(Styled.Button).props().type).toBe(OPERATION_TYPE.CANCEL);
      expect(node.find(Styled.CancelIcon)).toExist();
    });

    it('should use the input type as aria label', () => {
      expect(node.find(Styled.Button).props().title).toBe('Cancel instance 1');
    });

    it('should execute the passed method when clicked', () => {
      node.find(Styled.Button).simulate('click');
      expect(mockOnClick).toHaveBeenCalled();
    });
  });
});
