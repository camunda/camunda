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
      analytics: {
        selection: {},
        hover: {}
      }
    }, diagramRendered: true};

    gatewayAnalysisState = {state: {
      heatmap: {
        data: heatmapData
      },
      analytics: {
        selection: {
          EndEvent: 'act1'
        },
        hover: {}
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
      getAll: sinon.stub().returnsThis(),
      some: sinon.stub(),
      hasMarker: sinon.stub(),
      getGraphics: sinon.stub().returns({
        querySelector: sinon.stub().returns({
          setAttribute: sinon.spy()
        })
      })
    };

    update = createCreateAnalyticsRendererFunction()({viewer});
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

  it('should add branch analysis overlay for hovered end event element', () => {
    const hoveredState = {
      state: {
        heatmap: {
          data: heatmapData
        },
        analytics: {
          selection: {},
          hover: {
            EndEvent: {
              elementId: 'act-1'
            }
          }
        }
      },
      diagramRendered: true
    };

    const elements = [
      {id: 'act-1'}
    ];

    viewer.forEach = Array.prototype.forEach.bind(elements);

    update(hoveredState);

    expect(addBranchOverlay.calledWith(viewer, 'act-1', heatmapData)).to.eql(true);
  });

  it('should not add branch analysis overlay for hovered end event type', () => {
    const hoveredState = {
      state: {
        heatmap: {
          data: heatmapData
        },
        analytics: {
          selection: {},
          hover: {
            EndEvent: {
              elementType: 'EndEvent'
            }
          }
        }
      },
      diagramRendered: true
    };

    const elements = [
      {
        id: 'act-1',
        type: 'EndEvent'
      }
    ];

    isValidElement = ({type}, expectedType) => type === expectedType;
    __set__('isValidElement', isValidElement);

    viewer.forEach = Array.prototype.forEach.bind(elements);

    update(hoveredState);

    expect(addBranchOverlay.called).to.eql(false);
  });

  it('should add overlay for hovered element', () => {
    update(initialState);
    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(addBranchOverlay.calledWith(viewer, diagramElement.id)).to.eql(true);
  });

  it('should add an overlay for a potentially selected endEvent', () => {
    update(gatewayAnalysisState);

    viewer.on.firstCall.args[1]({element: diagramElement});

    expect(addBranchOverlay.calledWith(viewer, gatewayAnalysisState.state.analytics.selection.EndEvent)).to.eql(true);
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
    const elements = [
      {id: 'act1'},
      {id: 'act3'}
    ];

    gatewayAnalysisState.state.analytics.selection.Gateway = 'act3';

    viewer.forEach = Array.prototype.forEach.bind(elements);

    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith(elements[0], 'highlight_selected')).to.eql(true);
    expect(viewer.addMarker.calledWith(elements[1], 'highlight_selected')).to.eql(true);
  });

  it('should highlight the hovered elements with a different class', () => {
    const elements = [
      {
        id: 'act1'
      },
      {
        id: 'act2',
        type: 'Gateway'
      },
      {
        id: 'act3',
        type: 'Gateway'
      }
    ];

    isValidElement = ({type}, expectedType) => type === expectedType;
    __set__('isValidElement', isValidElement);

    gatewayAnalysisState.state.analytics.hover = {
      Gateway: {
        elementType: 'Gateway'
      }
    };

    viewer.forEach = Array.prototype.forEach.bind(elements);

    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith(elements[1], 'hover-highlight')).to.eql(true);
    expect(viewer.addMarker.calledWith(elements[2], 'hover-highlight')).to.eql(true);
  });

  it('should show overlays if an element is selected', () => {
    const elements = [
      {
        id: 'act1'
      }
    ];

    viewer.forEach = Array.prototype.forEach.bind(elements);

    gatewayAnalysisState.state.analytics.selection.EndEvent = 'act1';
    update(gatewayAnalysisState);

    expect(addBranchOverlay.calledOnce).to.eql(true);
    expect(addBranchOverlay.calledWith(viewer, 'act1')).to.eql(true);
  });

  it('should remove previous overlays', () => {
    update(initialState);

    expect(removeOverlays.calledWith(viewer)).to.eql(true);
  });
});
