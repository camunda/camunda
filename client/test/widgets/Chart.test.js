import {Chart} from 'widgets/Chart';

import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

// There is a problem with how the tooltip is included that makes this not testable atm
// We should revise it once we upgrade d3-tip to v0.8 (currently unreleased)
describe.skip('<Chart>', () => {
  let node;

  it('should create a chart with an absolute scale', () => {
    node = mount(
      <Chart absoluteScale={true} data={[
        {key: 'a', value: 12},
        {key: 'b', value: 3}
      ]}/>
    );

    expect(node.querySelector('.bar')).to.exist;
    expect(node.querySelector('.axis-x')).to.exist;
    expect(node.querySelector('.axis-y')).to.exist;
  });

  it('should create a chart with a relative scale', () => {
    node = mount(
      <Chart absoluteScale={false} data={[
        {key: 'a', value: .12},
        {key: 'b', value: .3}
      ]}/>
    );

    expect(node.querySelector('.bar')).to.exist;
    expect(node.querySelector('.axis-x')).to.exist;
    expect(node.querySelector('.axis-y')).to.exist;
  });
});
