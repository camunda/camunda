import React from 'react';
import {Redirect} from 'react-router-dom';
import {shallow} from 'enzyme';

import {setResponseInterceptor} from 'modules/request';

import Authentication from './Authentication';

jest.mock('modules/request');

describe('Authentication', () => {
  let Child, node;

  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Authentication>
        <Child />
      </Authentication>
    );
  });

  it('should attach a responseInterceptor', () => {
    expect(setResponseInterceptor).toBeCalled();
  });

  it('should render children by default', () => {
    expect(node.state('forceRedirect')).toBe(false);
    expect(node.find(Child)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should set forceRedirect to true on failed response', () => {
    // when
    node.instance().interceptResponse({status: 401});
    node.update();

    // then
    expect(node.state('forceRedirect')).toBe(true);
    expect(node.find(Child)).toHaveLength(0);
    const RedirectNode = node.find(Redirect);
    expect(RedirectNode).toHaveLength(1);
    expect(RedirectNode.prop('to')).toBe('/login');
    expect(node).toMatchSnapshot();
  });

  it("should reset falseRedirect to false once it's set", () => {
    // when
    node.setState({forceRedirect: true});

    // then
    expect(node.state('forceRedirect')).toBe(false);
  });
});
