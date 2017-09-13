import React from 'react';
import {mount} from 'enzyme';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {DateFilterReact} from 'main/processDisplay/controls/filter/date/DateFilter';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<DateFilter>', () => {
  let wrapper;
  let callback;
  let filter;

  const start = '2016-12-01T00:00:00';
  const end = '2016-12-31T23:59:59';

  beforeEach(() => {
    filter = {
      start,
      end
    };

    callback = sinon.spy();

    wrapper = mount(<DateFilterReact filter={filter} onDelete={callback}/>);
  });

  it('contain the formatted start date', () => {
    expect(wrapper).to.contain.text('2016-12-01');
  });

  it('should contain the formatted end date', () => {
    expect(wrapper).to.contain.text('2016-12-31');
  });

  it('should strip any time information', () => {
    expect(wrapper).to.not.contain.text('00:00:00');
    expect(wrapper).to.not.contain.text('23:59:59');
  });

  it('should call the delete callback', () => {
    wrapper.find('button').simulate('click');

    expect(callback.calledOnce).to.eql(true);
  });
});
