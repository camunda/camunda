import {expect} from 'chai';
import sinon from 'sinon';
import {createCreateAnalyticsRendererFunction,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/Analytics';

describe('<Analytics>', () => {
  let viewer;
  let update;
  let setEndEvent;
  let setGateway;
  let resetStatisticData;
  let leaveGatewayAnalysisMode;
  let addBranchOverlay;
  let $document;
  let hoverElement;
  let removeOverlays;
  let showSelectedOverlay;

  let heatmapData;
  let diagramElement;
  let endEvent;
  let gateway;
  let initialState;
  let gatewayAnalysisState;
  let isBpmnType;
  let integrator;

  beforeEach(() => {
    heatmapData = {
      piCount: 7,
      flowNodes: {
        act1: 1,
        act2: 2,
        act3: 3
      }
    };

    diagramElement = {businessObject: {
      type: 'bpmn:Task',
      name: 'Some Task',
      id: 'act2'
    }};

    endEvent = {businessObject: {
      type: 'bpmn:EndEvent',
      name: 'Some End Event',
      id: 'act1'
    }};

    gateway = {businessObject: {
      type: 'bpmn:Gateway',
      name: 'Some Gateway',
      id: 'act3'
    }};

    initialState = {state: {
      heatmap: {
        data: heatmapData
      },
      selection: {}
    }, diagramRendered: true};

    gatewayAnalysisState = {state: {
      heatmap: {
        data: heatmapData
      },
      selection: {
        endEvent: 'act1'
      }
    }, diagramRendered: true};

    $document = {
      addEventListener: sinon.spy(),
      removeEventListener: sinon.spy()
    };
    __set__('$document', $document);

    removeOverlays = sinon.spy();
    __set__('removeOverlays', removeOverlays);

    showSelectedOverlay = sinon.spy();
    __set__('showSelectedOverlay', showSelectedOverlay);

    addBranchOverlay = sinon.spy();
    __set__('addBranchOverlay', addBranchOverlay);

    setEndEvent = sinon.spy();
    __set__('setEndEvent', setEndEvent);

    hoverElement = sinon.spy();
    __set__('hoverElement', hoverElement);

    setGateway = sinon.spy();
    __set__('setGateway', setGateway);

    resetStatisticData = sinon.spy();
    __set__('resetStatisticData', resetStatisticData);

    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);

    isBpmnType = sinon.stub().returns(false);
    isBpmnType.withArgs(endEvent, 'EndEvent').returns(true);
    isBpmnType.withArgs(gateway, 'Gateway').returns(true);
    __set__('isBpmnType', isBpmnType);

    viewer = {
      get: sinon.stub().returnsThis(),
      on: sinon.spy(),
      addMarker: sinon.spy(),
      removeMarker: sinon.spy(),
      forEach: sinon.stub(),
      clear: sinon.spy(),
      getGraphics: sinon.stub().returns({
        querySelector: sinon.stub().returns({
          setAttribute: sinon.spy()
        })
      })
    };

    integrator = {
      unhover: sinon.spy(),
      hover: sinon.spy()
    };

    update = createCreateAnalyticsRendererFunction(integrator)({viewer});
  });

  afterEach(() => {
    __ResetDependency__('createModal');
    __ResetDependency__('isBpmnType');
    __ResetDependency__('removeOverlays');
    __ResetDependency__('showSelectedOverlay');
    __ResetDependency__('addBranchOverlay');
    __ResetDependency__('setEndEvent');
    __ResetDependency__('setGateway');
    __ResetDependency__('hoverElement');
    __ResetDependency__('leaveGatewayAnalysisMode');
    __ResetDependency__('GATEWAY_ANALYSIS_MODE');
    __ResetDependency__('$document');
    __ResetDependency__('resetStatisticData');
  });

  it('should process element hovering', () => {
    update(initialState);
    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(hoverElement.calledWith(viewer, diagramElement)).to.eql(true);
  });

  describe('integrator', () => {
    beforeEach(() => {
      update(initialState);
    });

    it('should unhover all previous hovers on hover', () => {
      viewer.on.firstCall.args[1]({element: diagramElement});
      expect(integrator.unhover.calledTwice).to.eql(true);
    });

    it('should call the hover with the appropriate type (EndEvent)', () => {
      viewer.on.firstCall.args[1]({element: endEvent});
      expect(integrator.hover.calledWith('EndEvent')).to.eql(true);
    });

    it('should call the hover with the appropriate type (Gateway)', () => {
      viewer.on.firstCall.args[1]({element: gateway});
      expect(integrator.hover.calledWith('Gateway')).to.eql(true);
    });
  });

  it('should do nothing when a non end event is clicked', () => {
    update(initialState);
    viewer.on.secondCall.args[1]({element: diagramElement});

    expect(setGateway.called).to.eql(false);
    expect(setEndEvent.called).to.eql(false);
  });

  it('should set the end event when an end event is clicked', () => {
    update(initialState);
    viewer.on.secondCall.args[1]({element: endEvent});

    expect(setEndEvent.calledWith(endEvent)).to.eql(true);
  });

  it('should set a gateway when a gateway is clicked', () => {
    update(gatewayAnalysisState);
    viewer.on.secondCall.args[1]({element: gateway});

    expect(setGateway.calledWith(gateway)).to.eql(true);
  });

  it('should reset potentially existing statistics data when a gateway is selected', () => {
    update(gatewayAnalysisState);
    viewer.on.secondCall.args[1]({element: gateway});

    expect(resetStatisticData.called).to.eql(true);
  });

  it('should reset potentially existing statistics data when an end event is selected', () => {
    update(gatewayAnalysisState);
    viewer.on.secondCall.args[1]({element: endEvent});

    expect(resetStatisticData.called).to.eql(true);
  });

  it('should highlight end events', () => {
    viewer.forEach.callsArgWith(0, endEvent);
    update(initialState);

    expect(viewer.addMarker.calledWith(endEvent, 'highlight')).to.eql(true);
  });

  it('should highlight gateways', () => {
    viewer.forEach.callsArgWith(0, gateway);
    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith(gateway, 'highlight')).to.eql(true);
  });

  it('should highlight the selected elements with a different class', () => {
    gatewayAnalysisState.state.selection.gateway = 'act3';
    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith('act3', 'highlight_selected')).to.eql(true);
    expect(viewer.addMarker.calledWith('act1', 'highlight_selected')).to.eql(true);
  });

  it('should show overlays if an element is selected', () => {
    gatewayAnalysisState.state.selection.endEvent = 'act1';
    update(gatewayAnalysisState);

    expect(showSelectedOverlay.calledOnce).to.eql(true);
    expect(showSelectedOverlay.calledWith(viewer, 'act1')).to.eql(true);
  });

  it('should handle overlays', () => {
    update(initialState);

    expect(removeOverlays.calledWith(viewer)).to.eql(true);
    expect(addBranchOverlay.calledWith(viewer, heatmapData)).to.eql(true);
  });
});
