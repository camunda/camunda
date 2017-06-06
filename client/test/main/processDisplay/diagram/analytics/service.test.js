import {expect} from 'chai';
import {
  toggleEndEvent, unsetElement, toggleGateway, leaveGatewayAnalysisMode,
  addBranchOverlay, BRANCH_OVERLAY, isValidElement,
  addHighlight, removeHighlights, __set__, __ResetDependency__
} from 'main/processDisplay/diagram/analytics/service';
import sinon from 'sinon';

describe('Analytics service', () => {
  const ENTER_GATEWAY_ANALYSIS_MODE = 'ENTER_GATEWAY_ANALYSIS_MODE';
  const UNSET_ELEMENT = 'UNSET_ELEMENT';
  const TOGGLE_ELEMENT = 'TOGGLE_ELEMENT';

  let heatmapData;
  let dispatchAction;
  let createEnterGatewayAnalysisModeAction;
  let createUnsetElementAction;
  let createToggleElementAction;
  let updateOverlayVisibility;
  let isBpmnType;
  let overlayNode;

  let viewer;
  let diagramGraphics;
  let viewbox;

  let overlaysMock;
  let elementRegistryMock;
  let canvasMock;

  beforeEach(() => {
    heatmapData = {
      piCount: 10,
      flowNodes: {
        a1: 5
      }
    };

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    updateOverlayVisibility = sinon.spy();
    __set__('updateOverlayVisibility', updateOverlayVisibility);

    createEnterGatewayAnalysisModeAction = sinon.stub().returns(ENTER_GATEWAY_ANALYSIS_MODE);
    __set__('createEnterGatewayAnalysisModeAction', createEnterGatewayAnalysisModeAction);

    isBpmnType = sinon.stub().returns(true);
    __set__('isBpmnType', isBpmnType);

    createUnsetElementAction = sinon.stub().returns(UNSET_ELEMENT);
    __set__('createUnsetElementAction', createUnsetElementAction);

    createToggleElementAction = sinon.stub().returns(TOGGLE_ELEMENT);
    __set__('createToggleElementAction', createToggleElementAction);

    overlayNode = {
      html: document.createElement('div')
    };

    diagramGraphics = document.createElement('div');
    diagramGraphics.innerHTML = '<div class="djs-hit" height="10" width="10"></div>';

    elementRegistryMock = {
      get: sinon.stub().returns({
        x: 0,
        y: 0
      }),
      getGraphics: sinon.stub().returns(diagramGraphics)
    };
    overlaysMock = {
      get: sinon.stub().returnsThis(),
      filter: sinon.stub().returnsThis(),
      forEach: sinon.stub().callsArgWith(0, overlayNode),
      add: sinon.spy()
    };
    viewbox = {
      x: 0,
      y: 0,
      width: 1024,
      height: 768
    };
    canvasMock = {
      viewbox: sinon.stub().returns(viewbox)
    };

    viewer = {
      get: sinon.stub()
    };
    viewer.get.withArgs('elementRegistry').returns(elementRegistryMock);
    viewer.get.withArgs('overlays').returns(overlaysMock);
    viewer.get.withArgs('canvas').returns(canvasMock);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('updateOverlayVisibility');
    __ResetDependency__('isBpmnType');
    __ResetDependency__('createEnterGatewayAnalysisModeAction');
    __ResetDependency__('createUnsetElementAction');
    __ResetDependency__('createToggleElementAction');
  });

  it('should toggle the end event', () => {
    const element = {id: 'element'};

    toggleEndEvent(element);

    expect(dispatchAction.calledWith(TOGGLE_ELEMENT)).to.eql(true);
    expect(createToggleElementAction.calledWith('element', 'EndEvent')).to.eql(true);
  });

  it('should toggle the gateway', () => {
    const element = {id: 'element'};

    toggleGateway(element);

    expect(dispatchAction.calledWith(TOGGLE_ELEMENT)).to.eql(true);
    expect(createToggleElementAction.calledWith('element', 'Gateway')).to.eql(true);
  });

  describe('unsetElement', () => {
    it('should unset the element type', () => {
      const type = 'some-type';

      unsetElement(type);

      expect(dispatchAction.calledWith(UNSET_ELEMENT)).to.eql(true);
      expect(createUnsetElementAction.calledWith(type)).to.eql(true);
    });
  });

  it('should unset the gateway and end event when leaving the gateway analysis mode', () => {
    leaveGatewayAnalysisMode();

    expect(dispatchAction.calledTwice).to.eql(true);
    expect(createUnsetElementAction.calledWith('EndEvent')).to.eql(true);
    expect(createUnsetElementAction.calledWith('Gateway')).to.eql(true);
  });

  describe('hover overlays', () => {
    it('should add overlays on the elements', () => {
      addBranchOverlay(viewer, 'a1', heatmapData);

      expect(overlaysMock.add.calledWith('a1')).to.eql(true);
    });

    it('should not add an overlay for a non-endEvent element', () => {
      isBpmnType.returns(false);
      addBranchOverlay(viewer, 'a1', heatmapData);

      expect(overlaysMock.add.calledWith('a1')).to.eql(false);
    });

    it('should position the overlay in the lower right edge by default', () => {
      addBranchOverlay(viewer, 'a1', heatmapData);

      const position = overlaysMock.add.firstCall.args[2].position;

      expect(position.bottom).to.exist;
      expect(position.right).to.exist;
    });

    it('should position the overlay in the top right edge if at the bottom of the screen', () => {
      viewbox.y = -viewbox.height;
      addBranchOverlay(viewer, 'a1', heatmapData);

      const position = overlaysMock.add.firstCall.args[2].position;

      expect(position.bottom).to.not.exist;
      expect(position.top).to.exist;
      expect(position.right).to.exist;
    });

    it('should position the overlay in the top left edge if at the bottom right of the screen', () => {
      viewbox.y = -viewbox.height;
      viewbox.x = -viewbox.width;
      addBranchOverlay(viewer, 'a1', heatmapData);

      const position = overlaysMock.add.firstCall.args[2].position;

      expect(position.bottom).to.not.exist;
      expect(position.right).to.not.exist;
      expect(position.top).to.exist;
      expect(position.left).to.exist;
    });

    it('should add an overlay with the piCount, element value and percentage as text content', () => {
      addBranchOverlay(viewer, 'a1', heatmapData);

      const node = overlaysMock.add.getCall(0).args[2].html;

      expect(node.textContent).to.contain('10');
      expect(node.textContent).to.contain('5');
      expect(node.textContent).to.contain('50%');
    });

    it('should add the overlay with the correct type', () => {
      addBranchOverlay(viewer, 'a1', heatmapData);

      const type = overlaysMock.add.getCall(0).args[1];

      expect(type).to.eql(BRANCH_OVERLAY);
    });

    it('should add an overlay for endEvents without reached instances', () => {
      heatmapData.flowNodes.a1 = 0;
      addBranchOverlay(viewer, 'a1', heatmapData);

      expect(overlaysMock.add.calledWith('a1')).to.eql(true);
    });

    it('should set the z-index property depending on the onTop parameter', () => {
      addBranchOverlay(viewer, 'a1', heatmapData, false);
      addBranchOverlay(viewer, 'a1', heatmapData, true);

      const zIndexBelow = overlaysMock.add.getCall(0).args[2].html.style.zIndex;
      const zIndexAbove = overlaysMock.add.getCall(1).args[2].html.style.zIndex;

      expect(zIndexBelow).to.be.below(zIndexAbove);
    });
  });

  describe('isValidElement', () => {
    let gatewayTwoOutgoing;
    let gatewayOneOutgoing;
    let endEvent;

    beforeEach(() => {
      gatewayTwoOutgoing = {
        businessObject: {
          outgoing: [1, 2]
        }
      };
      gatewayOneOutgoing = {
        businessObject: {
          outgoing: [1]
        }
      };
      endEvent = {};

      isBpmnType.returns(false);
      isBpmnType.withArgs(gatewayOneOutgoing, 'Gateway').returns(true);
      isBpmnType.withArgs(gatewayTwoOutgoing, 'Gateway').returns(true);
      isBpmnType.withArgs(endEvent, 'EndEvent').returns(true);
    });

    it('should be valid for every endEvent', () => {
      expect(isValidElement(endEvent, 'EndEvent')).to.be.true;
    });

    it('should be valid for gateways with multiple outgoing sequence flows', () => {
      expect(isValidElement(gatewayTwoOutgoing, 'Gateway')).to.be.true;
    });

    it('should not be valid for gateways with only one outgoing sequence flow', () => {
      expect(isValidElement(gatewayOneOutgoing, 'Gateway')).to.be.false;
    });
  });

  describe('addHighlight', () => {
    const ADD_HIGHLIGHT = 'ADD_HIGHLIGHT';
    let createAddHighlightAction;

    beforeEach(() => {
      createAddHighlightAction = sinon.stub().returns(ADD_HIGHLIGHT);
      __set__('createAddHighlightAction', createAddHighlightAction);
    });

    afterEach(() => {
      __ResetDependency__('createAddHighlightAction');
    });

    it('should dispatch add highlight action', () => {
      const elementType = 'ty1';
      const elementId = 'dd';

      addHighlight(elementType, elementId);

      expect(createAddHighlightAction.calledWith(elementType, elementId)).to.eql(true);
      expect(dispatchAction.calledWith(ADD_HIGHLIGHT)).to.eql(true);
    });
  });

  describe('removeHighlights', () => {
    const REMOVE_HIGHLIGHTS = 'REMOVE_HIGHLIGHTS';
    let createRemoveHighlightsAction;

    beforeEach(() => {
      createRemoveHighlightsAction = sinon.stub().returns(REMOVE_HIGHLIGHTS);
      __set__('createRemoveHighlightsAction', createRemoveHighlightsAction);
    });

    afterEach(() => {
      __ResetDependency__('createRemoveHighlightsAction');
    });

    it('should dispatch remove highlights action', () => {
      removeHighlights();

      expect(createRemoveHighlightsAction.called).to.eql(true);
      expect(dispatchAction.calledWith(REMOVE_HIGHLIGHTS)).to.eql(true);
    });
  });
});
