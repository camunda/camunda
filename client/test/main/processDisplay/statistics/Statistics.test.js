import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Statistics, __set__, __ResetDependency__} from 'main/processDisplay/statistics/Statistics';

describe('<Statistics>', () => {
  let node;
  let update;
  let leaveGatewayAnalysisMode;

  beforeEach(() => {
    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);

    ({node, update} = mountTemplate(<Statistics />));
  });

  afterEach(() => {
    __ResetDependency__('leaveGatewayAnalysisMode');
  });

  it('should not have the open class if gateway is not set', () => {
    update({});

    const container = node.querySelector('.statisticsContainer');

    expect(Array.from(container.classList)).to.not.include('open');
  });

  it('should have the open class when endEvent and gateway are set', () => {
    update({selection: {endEvent: 'a', gateway: 'b'}});

    const container = node.querySelector('.statisticsContainer');

    expect(Array.from(container.classList)).to.include('open');
  });

  it('should leave gateway analysis mode when close button is clicked', () => {
    update({});

    triggerEvent({
      node: node,
      selector: '.close',
      eventName: 'click'
    });

    expect(leaveGatewayAnalysisMode.called).to.eql(true);
  });
});
