import React from 'react';
import {shallow} from 'enzyme';

import {ExpandProvider} from './ExpandContext';

describe('ExpandContext', () => {
  const FooChild = () => <div>Foo</div>;

  describe('ExpandProvider', () => {
    it('should have expandedId null initially', () => {
      // given
      const instance = new ExpandProvider();

      // then
      expect(instance.state).toEqual({expandedId: null});
    });

    describe('expand', () => {
      it('should set expandedId to provided id', () => {
        // given
        const node = shallow(
          <ExpandProvider>
            <FooChild />
          </ExpandProvider>
        );

        // when
        node.instance().expand('foo');

        // then
        expect(node.instance().state.expandedId).toBe('foo');
      });
    });

    describe('resetExpanded', () => {
      it('should set expandedId to null', () => {
        // given
        const node = shallow(
          <ExpandProvider>
            <FooChild />
          </ExpandProvider>
        );
        const nodeInstance = node.instance();
        node.setState({expandedId: 'foo'});

        // then
        expect(nodeInstance.state.expandedId).toBe('foo');

        // when
        nodeInstance.resetExpanded();

        // then
        expect(nodeInstance.state.expandedId).toBe(null);
      });
    });
  });
});
