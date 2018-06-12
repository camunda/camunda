import React from 'react';
import {shallow} from 'enzyme';

import {setResponseInterceptor} from 'modules/request';

import Authentication from './Authentication';

jest.mock('modules/request');

describe('Authentication', () => {
  let Child, wrapper;

  beforeEach(() => {
    // given
    Child = () => <span>I am a child component</span>;
    wrapper = shallow(
      <Authentication>
        <Child />
      </Authentication>
    );
  });

  it('should attach a responseInterceptor', () => {
    // then
    expect(setResponseInterceptor).toBeCalled();
  });

  it('should render children by default', () => {
    // then
    expect(wrapper.state('forceRedirect')).toBe(false);
    expect(wrapper.find(Child)).toHaveLength(1);
    expect(wrapper).toMatchSnapshot();
  });

  it('should set forceRedirect to true on failed response', () => {
    // when
    wrapper.instance().interceptResponse({status: 401});
    wrapper.update();

    // then
    expect(wrapper.state('forceRedirect')).toBe(true);
    expect(wrapper.find(Child)).toHaveLength(0);
    expect(wrapper).toMatchSnapshot();
  });

  it("should reset falseRedirect to false once it's set", () => {
    // when
    wrapper.setState({forceRedirect: true});

    // then
    expect(wrapper.state('forceRedirect')).toBe(false);
  });
});
