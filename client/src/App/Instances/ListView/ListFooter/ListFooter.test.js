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
        addNewSelection={jest.fn()}
        addToCurrentSelection={jest.fn()}
        perPage={10}
        firstElement={0}
        total={6}
      />
    );

    expect(node.find(Paginator).length).toBe(0);
  });

  it('should add new selection', () => {
    const spy = jest.fn();
    const node = shallow(
      <Footer
        getStateLocally={() => {
          return {selections: []};
        }}
        storeStateLocally={() => {}}
        onFirstElementChange={jest.fn()}
        addNewSelection={spy}
        addToCurrentSelection={jest.fn()}
        perPage={10}
        firstElement={30}
        total={100}
      />
    );

    const createSelection = node.find('Dropdown').childAt(1);

    createSelection.simulate('click');

    expect(spy).toHaveBeenCalled();
  });
});
