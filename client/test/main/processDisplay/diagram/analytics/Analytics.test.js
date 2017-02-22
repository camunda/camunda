import {expect} from 'chai';
import sinon from 'sinon';
import {createMockComponent, mountTemplate} from 'testHelpers';
import {createAnalyticsRenderer,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/Analytics';

describe('<Analytics>', () => {
  let createModal;
  let viewer;
  let Modal;
  let update;
  let setEndEvent;
  let setGateway;
  let leaveGatewayAnalysisMode;
  let createReferenceComponent;
  let $document;

  let diagramElement;
  let endEvent;
  let gateway;
  let initialState;
  let gatewayAnalysisState;
  let is;

  const GATEWAY_ANALYSIS_MODE = 'GATEWAY_ANALYSIS_MODE';

  beforeEach(() => {
    diagramElement = {
      type: 'bpmn:Task',
      name: 'Some Task',
      id: 'act2'
    };

    endEvent = {
      type: 'bpmn:EndEvent',
      name: 'Some End Event',
      id: 'act1'
    };

    gateway = {
      type: 'bpmn:Gateway',
      name: 'Some Gateway',
      id: 'act3'
    };

    initialState = {state: {
      heatmap: {
        data: {
          act1: 1,
          act2: 2,
          act3: 3
        }
      },
      mode: null
    }, diagramRendered: true};

    gatewayAnalysisState = {state: {
      heatmap: {
        data: {
          act1: 1,
          act2: 2,
          act3: 3
        }
      },
      mode: GATEWAY_ANALYSIS_MODE,
      endEvent: 'act1'
    }, diagramRendered: true};

    $document = {
      addEventListener: sinon.spy(),
      removeEventListener: sinon.spy()
    };
    __set__('$document', $document);

    createReferenceComponent = (nodes) => {
      nodes.name = {textContent: ''};
      nodes.counterAll = {textContent: ''};
      nodes.counterReached = {textContent: ''};
      nodes.counterReachedPercentage = {textContent: ''};
    };
    __set__('createReferenceComponent', createReferenceComponent);

    setEndEvent = sinon.spy();
    __set__('setEndEvent', setEndEvent);

    setGateway = sinon.spy();
    __set__('setGateway', setGateway);

    leaveGatewayAnalysisMode = sinon.spy();
    __set__('leaveGatewayAnalysisMode', leaveGatewayAnalysisMode);

    __set__('GATEWAY_ANALYSIS_MODE', GATEWAY_ANALYSIS_MODE);

    Modal = createMockComponent('Modal');
    Modal.open = sinon.spy();
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    is = sinon.stub().returns(false);
    is.withArgs(endEvent, 'EndEvent').returns(true);
    is.withArgs(gateway, 'Gateway').returns(true);
    __set__('is', is);

    viewer = {
      get: sinon.stub().returnsThis(),
      on: sinon.spy(),
      addMarker: sinon.spy(),
      removeMarker: sinon.spy(),
      forEach: sinon.stub(),
      getGraphics: sinon.stub().returns({
        querySelector: sinon.stub().returns({
          setAttribute: sinon.spy()
        })
      })
    };

    ({update} = mountTemplate((node, eventsBus) => createAnalyticsRenderer({viewer, node, eventsBus})));
  });

  afterEach(() => {
    __ResetDependency__('createModal');
    __ResetDependency__('is');
    __ResetDependency__('setEndEvent');
    __ResetDependency__('setGateway');
    __ResetDependency__('leaveGatewayAnalysisMode');
    __ResetDependency__('GATEWAY_ANALYSIS_MODE');
    __ResetDependency__('$document');
    __ResetDependency__('createReferenceComponent');
  });

  it('should do nothing when a non end event is clicked', () => {
    update(initialState);
    viewer.on.lastCall.args[1]({element: diagramElement});

    expect(Modal.open.called).to.eql(false);
  });

  it('should set the end event and open modal when an end event is clicked', () => {
    update(initialState);
    viewer.on.lastCall.args[1]({element: endEvent});

    expect(setEndEvent.calledWith(endEvent)).to.eql(true);
    expect(Modal.open.called).to.eql(true);
  });

  it('should not set an end event in gateway analysis mode', () => {
    update(gatewayAnalysisState);
    viewer.on.lastCall.args[1]({element: endEvent});

    expect(setEndEvent.called).to.eql(false);
  });

  it('should set a gateway when a gateway is clicked', () => {
    update(gatewayAnalysisState);
    viewer.on.lastCall.args[1]({element: gateway});

    expect(setGateway.calledWith(gateway)).to.eql(true);
  });

  it('should not set a gateway outside of gateway analysis mode', () => {
    update(initialState);
    viewer.on.lastCall.args[1]({element: gateway});

    expect(setGateway.called).to.eql(false);
  });

  it('should leave gateway analysis mode when esc is pressed', () => {
    update(gatewayAnalysisState);
    $document.addEventListener.lastCall.args[1]({keyCode: 27});

    expect(leaveGatewayAnalysisMode.called).to.eql(true);
  });

  it('should not leave gateway analysis mode when a gateway is already selected', () => {
    gatewayAnalysisState.state.gateway = 'act3';
    update(gatewayAnalysisState);
    $document.addEventListener.lastCall.args[1]({keyCode: 27});

    expect(leaveGatewayAnalysisMode.called).to.eql(false);
  });

  it('should highlight end events', () => {
    viewer.forEach.callsArgWith(0, endEvent);
    update(initialState);

    expect(viewer.addMarker.calledWith(endEvent, 'highlight')).to.eql(true);
  });

  it('should highlight gateways in gateway analysis mode', () => {
    viewer.forEach.callsArgWith(0, gateway);
    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith(gateway, 'highlight')).to.eql(true);
  });

  it('should highlight the selected elements with a different class', () => {
    gatewayAnalysisState.state.gateway = 'act3';
    update(gatewayAnalysisState);

    expect(viewer.addMarker.calledWith('act3', 'highlight_selected')).to.eql(true);
    expect(viewer.addMarker.calledWith('act1', 'highlight_selected')).to.eql(true);
  });
});
