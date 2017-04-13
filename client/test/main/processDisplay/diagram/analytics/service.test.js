import {expect} from 'chai';
import {toggleEndEvent, unsetEndEvent, toggleGateway, unsetGateway, leaveGatewayAnalysisMode,
        hoverElement, addBranchOverlay, BRANCH_OVERLAY, showSelectedOverlay, isValidElement,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/service';
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

  let overlaysMock;
  let elementRegistryMock;

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

    elementRegistryMock = [{id: 'a1'}];
    overlaysMock = {
      get: sinon.stub().returnsThis(),
      filter: sinon.stub().returnsThis(),
      forEach: sinon.stub().callsArgWith(0, overlayNode),
      add: sinon.spy()
    };

    viewer = {
      get: sinon.stub()
    };
    viewer.get.withArgs('elementRegistry').returns(elementRegistryMock);
    viewer.get.withArgs('overlays').returns(overlaysMock);
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
    expect(createToggleElementAction.calledWith('element', 'endEvent')).to.eql(true);
  });

  it('should toggle the gateway', () => {
    const element = {id: 'element'};

    toggleGateway(element);

    expect(dispatchAction.calledWith(TOGGLE_ELEMENT)).to.eql(true);
    expect(createToggleElementAction.calledWith('element', 'gateway')).to.eql(true);
  });

  it('should unset the end event', () => {
    unsetEndEvent();

    expect(dispatchAction.calledWith(UNSET_ELEMENT)).to.eql(true);
    expect(createUnsetElementAction.calledWith('endEvent')).to.eql(true);
  });

  it('should unset the gateway', () => {
    unsetGateway();

    expect(dispatchAction.calledWith(UNSET_ELEMENT)).to.eql(true);
    expect(createUnsetElementAction.calledWith('gateway')).to.eql(true);
  });

  it('should unset the gateway and end event when leaving the gateway analysis mode', () => {
    leaveGatewayAnalysisMode();

    expect(dispatchAction.calledTwice).to.eql(true);
    expect(createUnsetElementAction.calledWith('endEvent')).to.eql(true);
    expect(createUnsetElementAction.calledWith('gateway')).to.eql(true);
  });

  describe('hover overlays', () => {
    it('should add overlays on the elements', () => {
      addBranchOverlay(viewer, heatmapData);

      expect(overlaysMock.add.calledWith('a1')).to.eql(true);
    });

    it('should add an overlay with the piCount, element value and percentage as text content', () => {
      addBranchOverlay(viewer, heatmapData);

      const node = overlaysMock.add.getCall(0).args[2].html;

      expect(node.textContent).to.contain('10');
      expect(node.textContent).to.contain('5');
      expect(node.textContent).to.contain('50%');
    });

    it('should add the overlay with the correct type', () => {
      addBranchOverlay(viewer, heatmapData);

      const type = overlaysMock.add.getCall(0).args[1];

      expect(type).to.eql(BRANCH_OVERLAY);
    });

    it('should add an overlay for endEvents without reached instances', () => {
      heatmapData.flowNodes.a1 = 0;
      addBranchOverlay(viewer, heatmapData);

      expect(overlaysMock.add.calledWith('a1')).to.eql(true);
    });

    it('should update the overlay visibility on hover', () => {
      hoverElement(viewer, 'a1');

      expect(updateOverlayVisibility.calledWith(viewer, 'a1', BRANCH_OVERLAY)).to.eql(true);
    });

    it('should show a selected overlay and keep it open', () => {
      showSelectedOverlay(viewer, 'a1');

      expect(overlayNode.keepOpen).to.be.true;
      expect(overlayNode.html.style.display).to.eql('block');
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
});
