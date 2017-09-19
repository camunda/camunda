import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {OperatorButton, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/OperatorButton';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('<OperatorButton>', () => {
  let wrapper;
  let setOperator;
  let setValue;
  let labels;

  beforeEach(() => {
    setValue = sinon.spy();
    __set__('setValue', setValue);

    setOperator = sinon.spy();
    __set__('setOperator', setOperator);

    labels = {
      T: 'TEST',
      V: 'VALUE'
    };
    __set__('labels', labels);
  });

  afterEach(() => {
    __ResetDependency__('setValue');
    __ResetDependency__('setOperator');
    __ResetDependency__('labels');
  });

  it('should display the label of the supplied operator', () => {
    wrapper = mount(<OperatorButton operator="T" />);

    expect(wrapper).to.contain.text(labels.T);
  });

  it('should call setOperator when clicked', () => {
    wrapper = mount(<OperatorButton operator="T" />);

    wrapper.find('button').simulate('click');

    expect(setOperator.calledWith('T')).to.eql(true);
  });

  it('should display label of the implicit value if provided', () => {
    wrapper = mount(<OperatorButton operator="T" implicitValue="V" />);

    expect(wrapper).to.contain.text(labels.T + ' ' + labels.V);
  });

  it('should call setValue if implicit value is provided', () => {
    wrapper = mount(<OperatorButton operator="T" implicitValue="V" />);

    wrapper.find('button').simulate('click');

    expect(setValue.calledWith('V')).to.eql(true);
  });

  it('should call setValue if implicit value is provided but falsy', () => {
    wrapper = mount(<OperatorButton operator="T" implicitValue={false} />);

    wrapper.find('button').simulate('click');

    expect(setValue.calledWith(false)).to.eql(true);
  });

  it('should have the active class if operator is active', () => {
    wrapper = mount(<OperatorButton operator="T" selectedOperator="T" />);

    expect(
      wrapper.find('button')
    ).to.have.className('active');
  });

  it('should not have the active class if implicit value does not match', () => {
    wrapper = mount(<OperatorButton operator="T" implicitValue="V" selectedOperator="T" />);

    expect(
      wrapper.find('button')
    ).not.to.have.className('active');
  });

  it('should have the active class if operator and implicit value match', () => {
    wrapper = mount(<OperatorButton operator="T" implicitValue="V" selectedOperator="T" value="V" />);

    expect(
      wrapper.find('button')
    ).to.have.className('active');
  });
});
