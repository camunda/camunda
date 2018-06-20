import React from 'react';
import {shallow} from 'enzyme';

import StateIcon from 'modules/components/StateIcon';

import InstanceDetail from './InstanceDetail';

describe('InstanceDetail', () => {
  it('should render state icon and instance id', () => {
    // given
    const instanceMock = {id: 'foo'};
    const node = shallow(<InstanceDetail instance={instanceMock} />);

    // then
    const StateIconNode = node.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('instance')).toBe(instanceMock);
    expect(node.text()).toContain(instanceMock.id);
    expect(node).toMatchSnapshot();
  });
});
