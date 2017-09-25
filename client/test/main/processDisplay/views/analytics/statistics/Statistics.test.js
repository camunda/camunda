import React from 'react';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';
import {StatisticsReact, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/statistics/Statistics';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const jsx = React.createElement;
const {expect} = chai;

describe('<Statistics>', () => {
  let node;
  let leaveGatewayAnalysisMode;
  let unopenedState;
  let openedState;
  let StatisticChart;
  let DragHandle;
  let getBpmnViewer;
  let viewer;
  let findSequenceFlowBetweenGatewayAndActivity;
  const sequenceFlow = 'SEQUENCE_FLOW';

  beforeEach(() => {
    unopenedState = {
      selection: {},
      correlation: {},
      height: 0
    };

    openedState = {
      selection: {
        EndEvent: 'a',
        Gateway: 'b'
      },
      correlation: {
        data: {
          followingNodes: {
            foo: 12,
            bar: 1
          }
        }
      },
      height: 350
    };

    viewer = {
      get: sinon.stub().returnsThis(),
      businessObject: {},
      forEach: sinon.stub().callsArgWith(0, sequenceFlow),
      removeMarker: sinon.spy(),
      addMarker: sinon.spy()
    };
    getBpmnViewer = sinon.stub().returns(viewer);

    findSequenceFlowBetweenGatewayAndActivity = sinon.stub().returns(sequenceFlow);
    __set__('findSequenceFlowBetweenGatewayAndActivity', findSequenceFlowBetweenGatewayAndActivity);

    StatisticChart = createReactMock('StatisticChart');
    __set__('StatisticChart', StatisticChart);

    DragHandle = createReactMock('DragHandle');
    __set__('DragHandle', DragHandle);

    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);
  });

  afterEach(() => {
    __ResetDependency__('leaveGatewayAnalysisMode');
    __ResetDependency__('DragHandle');
    __ResetDependency__('StatisticChart');
    __ResetDependency__('findSequenceFlowBetweenGatewayAndActivity');
  });

  it('should not have the open class if gateway is not set', () => {
    node = mount(<StatisticsReact
      getBpmnViewer={getBpmnViewer}
      {...unopenedState}
    />);

    expect(node.find('.statisticsContainer')).to.be.present();
    expect(node.find('.statisticsContainer.open')).to.not.be.present();
  });

  describe('opened state', () => {
    beforeEach(() => {
      node = mount(<StatisticsReact
        getBpmnViewer={getBpmnViewer}
        {...openedState}
      />);
    });

    it('should have the open class when endEvent and gateway are set', () => {
      expect(node.find('.statisticsContainer.open')).to.be.present();
    });

    it('should leave gateway analysis mode when close button is clicked', () => {
      node.find('.close').simulate('click');

      expect(leaveGatewayAnalysisMode.called).to.eql(true);
    });

    it('should contain StatisticCharts', () => {
      expect(node).to.contain.text(StatisticChart.text);
    });

    it('should have a drag handle', () => {
      expect(node).to.contain.text(DragHandle.text);
    });

    it('should set its height based on the state', () => {
      node.setProps({height: 1234});
      expect(node.getDOMNode().style.height).to.eql('1234px');
    });

    it('should highlight sequence flows on hover', () => {
      node
        .find(StatisticChart)
        .first()
        .prop('chartConfig')
        .onHoverChange(true)({}, 0);
      expect(viewer.addMarker.calledWith(sequenceFlow, 'chart-hover')).to.eql(true);
    });

    it('should clear all highlights on any hover action', () => {
      node
        .find(StatisticChart)
        .first()
        .prop('chartConfig')
        .onHoverChange(false)({}, 0);
      expect(viewer.removeMarker.calledWith(sequenceFlow, 'chart-hover')).to.eql(true);
    });
  });
});
