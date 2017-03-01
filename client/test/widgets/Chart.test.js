import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import {Chart} from 'widgets/Chart';

// There is a problem with how the tooltip is included that makes this not testable atm
// We should revise it once we upgrade d3-tip to v0.8 (currently unreleased)
describe.skip('<Chart>', () => {
  let update;
  let node;

  it('should create a chart with an absolute scale', () => {
    ({update, node} = mountTemplate(
      <Chart config={{absoluteScale: true}} />
    ));
    update([
      {key: 'a', value: 12},
      {key: 'b', value: 3}
    ]);

    expect(node.querySelector('.bar')).to.exist;
    expect(node.querySelector('.axis-x')).to.exist;
    expect(node.querySelector('.axis-y')).to.exist;
  });

  it('should create a chart with a relative scale', () => {
    ({update, node} = mountTemplate(
      <Chart config={{absoluteScale: false}} />
    ));
    update([
      {key: 'a', value: .12},
      {key: 'b', value: .3}
    ]);

    expect(node.querySelector('.bar')).to.exist;
    expect(node.querySelector('.axis-x')).to.exist;
    expect(node.querySelector('.axis-y')).to.exist;
  });
});
