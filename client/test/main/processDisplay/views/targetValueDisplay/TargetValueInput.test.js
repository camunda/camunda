import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {TargetValueInput} from 'main/processDisplay/views/targetValueDisplay/TargetValueInput';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<TargetValueInput>', () => {
  it('should contain an input field', () => {
    const node = mount(<TargetValueInput unit="d" />);

    expect(node.find('input')).to.exist;
  });

  it('should set the max attribute based on the passed unit', () => {
    const node1 = mount(<TargetValueInput unit="d" />);
    const node2 = mount(<TargetValueInput unit="s" />);

    expect(node1
      .find('input')
      .getDOMNode()
      .getAttribute('max')).to.eql('6');
    expect(node2
      .find('input')
      .getDOMNode()
      .getAttribute('max')).to.eql('59');
  });
});
