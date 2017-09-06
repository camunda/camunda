import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {Loader} from 'widgets/Loader.react';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<Loader>', () => {
  const className = 'some-class';
  const style = {
    textAlign: 'right'
  };
  let wrapper;

  beforeEach(() => {
    wrapper = mount(<Loader visible={true} className={className} style={style} />);
  });

  it('should display spinner', () => {
    expect(wrapper.find('.spinner')).to.be.present();
  });

  it('should display loading', () => {
    expect(wrapper).to.contain.text('loading');
  });

  it('should add custom class name', () => {
    expect(wrapper.find(`.${className}`)).to.be.present();
  });

  it('should add custom style', () => {
    expect(wrapper).to.have.style('text-align', 'right');
  });

  it('should be possible to specify custom loading text', () => {
    const text = 'some text';

    wrapper = mount(<Loader text={text} visible={true} />);

    expect(wrapper).to.contain.text(text);
    expect(wrapper).not.to.contain.text('loading');
  });

  it('should not render loader when not visible', () => {
    wrapper = mount(<Loader visible={false} />);

    expect(wrapper.find('.loading_indicator')).not.to.be.present();
  });
});
