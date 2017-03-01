import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Statistics, __set__, __ResetDependency__} from 'main/processDisplay/statistics/Statistics';

describe('<Statistics>', () => {
  let node;
  let update;
  let leaveGatewayAnalysisMode;
  let unopenedState;
  let openedState;
  let StatisticChart;

  beforeEach(() => {
    unopenedState = {
      display: {
        selection: {}
      },
      statistics: {
        correlation: {}
      }
    };

    openedState = {
      display: {
        selection: {
          endEvent: 'a',
          gateway: 'b'
        }
      },
      statistics: {
        correlation: {}
      }
    };

    StatisticChart = createMockComponent('StatisticChart');
    __set__('StatisticChart', StatisticChart);

    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);

    ({node, update} = mountTemplate(<Statistics />));
  });

  afterEach(() => {
    __ResetDependency__('leaveGatewayAnalysisMode');
    __ResetDependency__('StatisticChart');
  });

  it('should not have the open class if gateway is not set', () => {
    update(unopenedState);

    const container = node.querySelector('.statisticsContainer');

    expect(Array.from(container.classList)).to.not.include('open');
  });

  it('should have the open class when endEvent and gateway are set', () => {
    update(openedState);

    const container = node.querySelector('.statisticsContainer');

    expect(Array.from(container.classList)).to.include('open');
  });

  it('should leave gateway analysis mode when close button is clicked', () => {
    update(openedState);

    triggerEvent({
      node: node,
      selector: '.close',
      eventName: 'click'
    });

    expect(leaveGatewayAnalysisMode.called).to.eql(true);
  });

  it('should contain StatisticCharts', () => {
    update(openedState);

    expect(node.textContent).to.contain('StatisticChart');
  });
});
