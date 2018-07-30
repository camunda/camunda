import React from 'react';
import {shallow} from 'enzyme';

import WrappedFooter from './ListFooter';
import Paginator from './Paginator';
const Footer = WrappedFooter.WrappedComponent;

describe('ListFooter', () => {
  it('should not show a pagination if there is only one page', () => {
    const node = shallow(
      <Footer
        getStateLocally={() => {
          return {selections: []};
        }}
        storeStateLocally={() => {}}
        onFirstElementChange={jest.fn()}
        onAddToSelection={jest.fn()}
        perPage={10}
        firstElement={0}
        total={6}
      />
    );

    expect(node.find(Paginator).length).toBe(0);
  });

  it('should pass the onAddToSelection function with the SelectAll button', () => {
    const spy = jest.fn();
    const node = shallow(
      <Footer
        getStateLocally={() => {
          return {selections: []};
        }}
        storeStateLocally={() => {}}
        onFirstElementChange={jest.fn()}
        onAddToSelection={spy}
        perPage={10}
        firstElement={30}
        total={100}
      />
    );
    const instance = node.instance();
    instance.handleSelectionInteraction();

    expect(spy).toHaveBeenCalled();
  });
});
