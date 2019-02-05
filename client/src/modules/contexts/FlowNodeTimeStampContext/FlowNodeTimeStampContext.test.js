import React from 'react';
import {mount} from 'enzyme';

import {
  FlowNodeTimeStampProvider,
  withFlowNodeTimeStampContext
} from './FlowNodeTimeStampContext';

const Foo = withFlowNodeTimeStampContext(function Foo() {
  return <div>foo</div>;
});

function mountNode() {
  return mount(
    <FlowNodeTimeStampProvider>
      <Foo />
    </FlowNodeTimeStampProvider>
  );
}

describe('FlowNodeTimeStampContext', () => {
  it('should add properties to the wrapped component', () => {
    // given
    const node = mountNode();
    expect(node.find(Foo).find('[showTimeStamp=false]')).toExist();
  });

  describe('handleTimeStampToggle', () => {
    it('should toggle the state', () => {
      //given
      const node = mountNode();
      expect(node.state('showTimeStamp')).toBe(false);

      //when
      node.instance().handleTimeStampToggle();

      //then
      expect(node.state('showTimeStamp')).toBe(true);
    });
  });
});
