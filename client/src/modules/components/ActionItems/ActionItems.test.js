import React from 'react';
import {shallow} from 'enzyme';

import ActionItems from './ActionItems';
import {ACTION_TYPE} from 'modules/constants';

import * as Styled from './styled';

describe('ActionItems', () => {
  let node;
  let mockOnClick = jest.fn();

  beforeEach(() => {
    node = shallow(
      <ActionItems>
        <ActionItems.Item
          type={ACTION_TYPE.RETRY}
          onClick={() => mockOnClick()}
        />
      </ActionItems>
    );
  });

  it('should render with its children', () => {
    expect(node.find(Styled.Ul)).toExist();
    expect(node.find(ActionItems.Item)).toExist();
  });

  describe('Item', () => {
    beforeEach(() => {
      node = shallow(
        <ActionItems.Item
          type={ACTION_TYPE.RETRY}
          onClick={() => mockOnClick()}
        />
      );
    });

    it('should show the correct icon based on the type', () => {
      // when
      expect(node.find(Styled.Button).props().type).toBe(ACTION_TYPE.RETRY);
      // then
      expect(node.find(Styled.RetryIcon)).toExist();
    });

    it('should use the input type as aria label', () => {
      expect(node.find(Styled.Button).props()['aria-label']).toBe(
        ACTION_TYPE.RETRY.toLowerCase()
      );
    });

    it('should execute the passed method when clicked', () => {
      node.find(Styled.Button).simulate('click');
      expect(mockOnClick).toHaveBeenCalled();
    });
  });
});
