import React from 'react';
import {shallow} from 'enzyme';

import {OPERATION_STATE} from 'modules/constants';

import ActionSpinner from './ActionSpinner';

import * as Styled from './styled';

describe('ActionSpinner', () => {
  let node;

  it('should render a spinner', () => {
    // when
    node = shallow(
      <ActionSpinner operationState={OPERATION_STATE.SCHEDULED} />
    );
    //then
    expect(node.find(Styled.ActionSpinner)).toExist();

    // when
    node = shallow(<ActionSpinner operationState={OPERATION_STATE.LOCKED} />);
    //then
    expect(node.find(Styled.ActionSpinner)).toExist();
  });

  it('should not render a spinner', () => {
    //
  });
});
