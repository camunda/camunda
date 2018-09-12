import React from 'react';
import {shallow} from 'enzyme';

import InstancePayload from './InstancePayload';
import {PLACEHOLDER} from './constants';
import * as Styled from './styled';

const PAYLOAD = {
  a: 'b',
  c: 'd',
  e: 'f'
};

describe('InstancePayload', () => {
  it('should render empty placeholder if there is no payload', () => {
    // given
    const node = shallow(<InstancePayload payload={null} />);

    // then
    expect(node.contains(PLACEHOLDER)).toBe(true);
    expect(node).toMatchSnapshot();
  });

  it('should render key value table based on payload', () => {
    // given
    const node = shallow(<InstancePayload payload={PAYLOAD} />);

    // then
    expect(node.contains(PLACEHOLDER)).toBe(false);

    // TH nodes
    const THNodes = node.find(Styled.TH);
    expect(THNodes).toHaveLength(2);
    expect(THNodes.at(0).contains('Key')).toBe(true);
    expect(THNodes.at(1).contains('Value')).toBe(true);

    // TR nodes
    const TRNodes = node.find(Styled.TR);
    expect(TRNodes).toHaveLength(4);
    TRNodes.forEach((TRNode, idx) => {
      // ignore first row for thead
      if (idx === 0) {
        return;
      }

      // key td
      const FirstTDNode = TRNode.find(Styled.TD).at(0);
      expect(FirstTDNode.contains(Object.keys(PAYLOAD)[idx - 1])).toBe(true);
      expect(FirstTDNode.prop('isBold')).toBe(true);

      // value td
      const SecondTDNode = TRNode.find(Styled.TD).at(1);
      expect(SecondTDNode.contains(Object.values(PAYLOAD)[idx - 1])).toBe(true);
      expect(SecondTDNode.prop('isBold')).not.toBe(true);
    });
    expect(node).toMatchSnapshot();
  });
});
