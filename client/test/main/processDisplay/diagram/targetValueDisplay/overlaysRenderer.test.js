import {expect} from 'chai';
import sinon from 'sinon';
import {createOverlaysRenderer, __set__, __ResetDependency__} from 'main/processDisplay/diagram/targetValueDisplay/overlaysRenderer';

describe('target value overlays renderer', () => {
  const heatmapNode = document.createElement('img');

  let state;
  let getHeatmap;
  let addTargetValueBadge;
  let addTargetValueTooltip;
  let viewer;
  let canvas;
  let eventBus;
  let elementRegistry;
  let element;
  let diagramNode;
  let removeOverlays;
  let update;
  let StateComponent;
  let ModalComponent;
  let prepareFlowNodes;
  let isBpmnType;
  let getTargetValue;
  let hoverElement;

  const PREPARED_FLOW_NODES = 'PREPARED_FLOW_NODES';

  beforeEach(() => {
    state = {
      heatmap: {
        data: {
          flowNodes: {
            a: 1,
            b: 2
          }
        }
      },
      targetValue: {
        data: {
          a: 3
        }
      }
    };

    getHeatmap = sinon.stub().returns(heatmapNode);
    __set__('getHeatmap', getHeatmap);

    removeOverlays = sinon.spy();
    __set__('removeOverlays', removeOverlays);

    addTargetValueBadge = sinon.spy();
    __set__('addTargetValueBadge', addTargetValueBadge);

    addTargetValueTooltip = sinon.spy();
    __set__('addTargetValueTooltip', addTargetValueTooltip);

    prepareFlowNodes = sinon.stub().returns(PREPARED_FLOW_NODES);
    __set__('prepareFlowNodes', prepareFlowNodes);

    isBpmnType = sinon.stub().returns(true);
    __set__('isBpmnType', isBpmnType);

    getTargetValue = sinon.stub().returns(0);
    __set__('getTargetValue', getTargetValue);

    hoverElement = sinon.spy();
    __set__('hoverElement', hoverElement);

    canvas = {
      addMarker: sinon.spy(),
      removeMarker: sinon.spy(),
      resized: sinon.spy(),
      zoom: sinon.spy(),
      _viewport: {
        appendChild: sinon.spy(),
        removeChild: sinon.spy()
      }
    };

    eventBus = {
      on: sinon.spy()
    };

    element = {
      businessObject: {
        id: 'a'
      }
    };

    diagramNode = document.createElement('div');
    diagramNode.innerHTML = '<div class="djs-outline"></div>';

    elementRegistry = {
      forEach: sinon.stub().callsArgWith(0, element),
      getGraphics: sinon.stub().returns(diagramNode)
    };

    const modules = {
      canvas,
      eventBus,
      elementRegistry
    };

    viewer = {
      get: (name) => {
        return modules[name];
      }
    };

    StateComponent = {};
    ModalComponent = {
      open: sinon.spy()
    };

    update = createOverlaysRenderer(StateComponent, ModalComponent)({viewer});
  });

  afterEach(() => {
    __ResetDependency__('getHeatmap');
    __ResetDependency__('removeOverlays');
    __ResetDependency__('addTargetValueTooltip');
    __ResetDependency__('addTargetValueBadge');
    __ResetDependency__('Diagram');
    __ResetDependency__('createDiagram');
    __ResetDependency__('prepareFlowNodes');
    __ResetDependency__('isBpmnType');
    __ResetDependency__('getTargetValue');
    __ResetDependency__('hoverElement');
  });

  it('should create a heatmap', () => {
    update({state, diagramRendered: true});
    expect(getHeatmap.calledWith(viewer, PREPARED_FLOW_NODES));
  });

  it('should open the modal when a valid element is clicked', () => {
    update({state, diagramRendered: true});
    const clickHandler = eventBus.on.firstCall.args[1];

    clickHandler({element});

    expect(ModalComponent.open.calledWith(element)).to.eql(true);
  });

  it('should not open the modal when a non-valid element is clicked', () => {
    isBpmnType.returns(false);
    update({state, diagramRendered: true});

    const clickHandler = eventBus.on.firstCall.args[1];

    clickHandler({element});

    expect(ModalComponent.open.called).to.eql(false);
  });

  it('should handle hovering of an element', () => {
    update({state, diagramRendered: true});
    const hoverHandler = eventBus.on.secondCall.args[1];

    hoverHandler({element});

    expect(hoverElement.calledWith(viewer, element)).to.eql(true);
  });

  it('should highlight valid elements', () => {
    update({state, diagramRendered: true});
    expect(canvas.addMarker.calledWith(element, 'highlight')).to.eql(true);
  });

  it('should create tooltips for all elements with data', () => {
    const TARGET_VALUE = 400;

    getTargetValue.returns(TARGET_VALUE);
    update({state, diagramRendered: true});

    expect(addTargetValueBadge.calledWith(viewer, element, TARGET_VALUE, ModalComponent.open)).to.eql(true);
  });

  it('should create badges for all elements with a target value', () => {
    const TARGET_VALUE = 400;

    getTargetValue.returns(TARGET_VALUE);
    update({state, diagramRendered: true});

    expect(addTargetValueTooltip.calledWith(viewer, element, 1, TARGET_VALUE)).to.eql(true);
  });
});
