import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {ProcessInstanceCount} from 'main/processDisplay/views/ProcessInstanceCount';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<ProcessInstanceCount>', () => {
  it('should display the instance count', () => {
    const node = mount(<ProcessInstanceCount data="123" />);

    expect(node.find('.count')).to.be.present();
    expect(node).to.contain.text('123');
  });

  it('should have a thousands separator', () => {
    const node = mount(<ProcessInstanceCount data="12345" />);

    expect(node).to.contain.text('12\u202F345');
  });
});
