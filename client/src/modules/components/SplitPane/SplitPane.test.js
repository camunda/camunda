import React from 'react';
import {shallow} from 'enzyme';

import SplitPane from './SplitPane';
import {ExpandProvider} from './ExpandContext';
import {PANE_ID} from './Pane/constants';

describe('SplitPane', () => {
  it('should render children wrapped in ExpandProvider', () => {
    // given
    const FirstChild = () => <div>First Child</div>;
    const SecondChild = () => <div>Second Child</div>;
    const node = shallow(
      <SplitPane>
        <FirstChild />
        <SecondChild />
      </SplitPane>
    );

    // then
    const ExpandProviderNode = node.find(ExpandProvider);
    expect(ExpandProviderNode).toHaveLength(1);
    const FirstChildNode = ExpandProviderNode.find(FirstChild);
    const SecondChildNode = ExpandProviderNode.find(SecondChild);
    expect(FirstChildNode).toHaveLength(1);
    expect(FirstChildNode.prop('paneId')).toBe(PANE_ID.TOP);
    expect(SecondChildNode).toHaveLength(1);
    expect(SecondChildNode.prop('paneId')).toBe(PANE_ID.BOTTOM);
    expect(node).toMatchSnapshot();
  });
});
