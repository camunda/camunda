import React from 'react';
import {shallow} from 'enzyme';
import withSharedState from './withSharedState';

const Component = () => {};

describe('withSharedState', () => {
  let node;

  beforeEach(() => {
    const Wrapped = withSharedState(Component);
    node = shallow(<Wrapped customProp="1234" />);
  });

  it('should contain the component with additional props', () => {
    const component = node.find(Component);

    expect(component).toExist();
    expect(component.prop('customProp')).toBe('1234');
    expect(component.prop('storeState')).toBeDefined();
    expect(component.prop('clearState')).toBeDefined();
    expect(component.prop('getState')).toBeDefined();
  });

  it('should store state in localstorage', () => {
    localStorage.setItem.mockClear();
    const data = {a: 1, b: 2};

    node.instance().storeState(data);

    expect(localStorage.setItem).toHaveBeenCalledWith(
      'sharedState',
      JSON.stringify(data)
    );
  });

  it('should retrieve localstorage state', () => {
    localStorage.getItem.mockImplementationOnce(() => '{"a": 1, "b": 2}');

    expect(node.instance().getState()).toEqual({a: 1, b: 2});
  });

  it('should clear localstorage state', () => {
    localStorage.removeItem.mockClear();

    node.instance().clearState();

    expect(localStorage.removeItem).toHaveBeenCalledWith('sharedState');
  });
});
