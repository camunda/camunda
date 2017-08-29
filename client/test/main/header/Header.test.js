import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import React from 'react';
import {mount} from 'enzyme';
import {HeaderReact, __set__, __ResetDependency__} from 'main/header/Header';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<Header>', () => {
  let wrapper;
  let router;

  beforeEach(() => {
    router = {
      goTo: sinon.spy()
    };

    __set__('router', router);

    (wrapper = mount(<HeaderReact/>));
  });

  afterEach(() => {
    __ResetDependency__('router');
  });

  it('should contain header text', () => {
    expect(wrapper).to.contain.text('Camunda Optimize');
  });

  it('should redirect to default route on click', () => {
    wrapper.find('.navbar-brand').simulate('click');

    expect(router.goTo.calledWith('default', {})).to.eql(true);
  });
});
