import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createReactMock} from 'testHelpers';
import {OperatorSelectionReact, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/OperatorSelection';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('<OperatorSelection>', () => {
  let wrapper;
  let OperatorButton;
  let variables;

  beforeEach(() => {
    variables = {
      data: [
        {type: 'Boolean'},
        {type: 'String'},
        {type: 'Number'}
      ]
    };

    OperatorButton = createReactMock('OperatorButton');
    __set__('OperatorButton', OperatorButton);
  });

  afterEach(() => {
    __ResetDependency__('OperatorButton');
  });

  it('should not display any operators if no variable is selected', () => {
    wrapper = mount(<OperatorSelectionReact variables={variables} />);

    expect(wrapper).to.not.contain.text(OperatorButton.text);
  });

  it('should have in and not in buttons for strings', () => {
    wrapper = mount(<OperatorSelectionReact variables={variables} selectedIdx={1} />);

    expect(wrapper).to.contain.text(OperatorButton.text);
    expect(OperatorButton.calledWith({operator: 'in'})).to.eql(true);
    expect(OperatorButton.calledWith({operator: 'not in'})).to.eql(true);
  });

  it('should have buttons with implicit values for booleans', () => {
    wrapper = mount(<OperatorSelectionReact variables={variables} selectedIdx={0} />);

    expect(wrapper).to.contain.text(OperatorButton.text);
    expect(OperatorButton.calledWith({implicitValue: false, operator: '='})).to.eql(true);
    expect(OperatorButton.calledWith({implicitValue: true, operator: '='})).to.eql(true);
  });

  it('should have in, not in, < and > operators for all other types (numbers and dates)', () => {
    wrapper = mount(<OperatorSelectionReact variables={variables} selectedIdx={2} />);

    expect(wrapper).to.contain.text(OperatorButton.text);
    expect(OperatorButton.calledWith({operator: 'in'})).to.eql(true, 'in');
    expect(OperatorButton.calledWith({operator: 'not in'})).to.eql(true, 'not in');
    expect(OperatorButton.calledWith({operator: '>'})).to.eql(true, '>');
    expect(OperatorButton.calledWith({operator: '<'})).to.eql(true, '<');
  });
});
