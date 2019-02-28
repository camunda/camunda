import React from 'react';
import {shallow} from 'enzyme';

import StateIcon from 'modules/components/StateIcon';

import InstanceDetail from './InstanceDetail';

describe('InstanceDetail', () => {
  it('should render state icon and instance id', () => {
    // given
    const instanceMock = {id: 'foo', state: 'ACTIVE'};
    const node = shallow(<InstanceDetail instance={instanceMock} />);

    // then
    const StateIconNode = node.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceMock.state);
    expect(node.text()).toContain(instanceMock.id);
    expect(node).toMatchSnapshot();
  });
});
