import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {withState} from 'utils/withState';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('utils withState', () => {
  let state;
  let InnerComponent;
  let Component;
  let wrapper;
  let innerWrapper;

  beforeEach(() => {
    state = {
      a: 1
    };

    InnerComponent = sinon.stub().returns(
      <div id="id-1">Something</div>
    );
    Component = withState(state, InnerComponent);

    wrapper = mount(<Component prop1={23} />);
    innerWrapper = wrapper.find(InnerComponent);
  });

  it('should include original component body', () => {
    expect(wrapper.find({id: 'id-1'})).to.exist;
    expect(wrapper).to.contain.text('Something');
  });

  it('should pass extra properties to inner component', () => {
    expect(innerWrapper).to.have.prop('prop1', 23);
  });

  it('should pass state as properties to InnerComponent', () => {
    expect(innerWrapper).to.have.prop('a', 1);
  });

  describe('setProperty', () => {
    it('should enable changing state from outer component method', () => {
      Component.setProperty('a', 234);

      expect(innerWrapper).to.have.prop('a', 234);
    });

    it('should enable setProperty function as property of inner component', () => {
      const setProperty = innerWrapper.prop('setProperty');

      setProperty('a', 452);

      expect(innerWrapper).to.have.prop('a', 452);
    });
  });
});
