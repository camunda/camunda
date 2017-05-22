import {expect} from 'chai';
import sinon from 'sinon';
import {createCreateAnalyticsRendererFunction,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/Analytics';

describe('<Analytics>', () => {
  let viewer;
  let update;
  let toggleEndEvent;
  let toggleGateway;
  let resetStatisticData;
  let leaveGatewayAnalysisMode;
  let addBranchOverlay;
  let $document;
  let removeOverlays;

  let heatmapData;
  let diagramElement;
  let endEvent;
  let gateway;
  let initialState;
  let gatewayAnalysisState;
  let isValidElement;
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

    addBranchOverlay = sinon.spy();
    __set__('addBranchOverlay', addBranchOverlay);

    toggleEndEvent = sinon.spy();
    __set__('toggleEndEvent', toggleEndEvent);

    toggleGateway = sinon.spy();
    __set__('toggleGateway', toggleGateway);

    resetStatisticData = sinon.spy();
    __set__('resetStatisticData', resetStatisticData);

    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);

    isValidElement = sinon.stub().returns(false);
    isValidElement.withArgs(endEvent, 'EndEvent').returns(true);
    isValidElement.withArgs(gateway, 'Gateway').returns(true);
    __set__('isValidElement', isValidElement);

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
    __ResetDependency__('isValidElement');
    __ResetDependency__('removeOverlays');
    __ResetDependency__('addBranchOverlay');
    __ResetDependency__('toggleEndEvent');
    __ResetDependency__('toggleGateway');
    __ResetDependency__('leaveGatewayAnalysisMode');
    __ResetDependency__('GATEWAY_ANALYSIS_MODE');
    __ResetDependency__('$document');
    __ResetDependency__('resetStatisticData');
  });

  it('should remove overlays when hovering over a new element', () => {
    update(initialState);
    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(removeOverlays.calledWith(viewer)).to.eql(true);
  });

  it('should add overlay for hovered element', () => {
    update(initialState);
    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(addBranchOverlay.calledWith(viewer, diagramElement.id)).to.eql(true);
  });

  it('should add an overlay for a potentially selected endEvent', () => {
    update(gatewayAnalysisState);

    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(addBranchOverlay.calledWith(viewer, gatewayAnalysisState.state.selection.endEvent)).to.eql(true);
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

    expect(toggleGateway.called).to.eql(false);
    expect(toggleEndEvent.called).to.eql(false);
  });

  it('should toggle end event when an end event is clicked', () => {
    update(initialState);
    viewer.on.secondCall.args[1]({element: endEvent});

    expect(toggleEndEvent.calledWith(endEvent)).to.eql(true);
  });

  it('should toggle gateway when a gateway is clicked', () => {
    update(gatewayAnalysisState);
    viewer.on.secondCall.args[1]({element: gateway});

    expect(toggleGateway.calledWith(gateway)).to.eql(true);
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

    expect(addBranchOverlay.calledOnce).to.eql(true);
    expect(addBranchOverlay.calledWith(viewer, 'act1')).to.eql(true);
  });

  it('should remove previous overlays', () => {
    update(initialState);

    expect(removeOverlays.calledWith(viewer)).to.eql(true);
  });
});
