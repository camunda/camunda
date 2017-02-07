import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {getHeatmap, hoverElement, removeHeatmapOverlay, addHeatmapOverlay,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/service';

describe('Diagram service', () => {
  const heatmap = {
    dimensions: {
      x: 0,
      y: 0,
      width: 100,
      height: 100
    },
    img: 'base64-encoded image'
  };
  const heatmapData = {
    act1: 1,
    act2: 2
  };
  const diagramElement = {id: 'act1'};

  const HOVER_ACTION = 'HOVER_ACTION';

  let generateHeatmap;
  let dispatchAction;
  let viewer;
  let clearFunction;
  let addFunction;
  let diagramGraphics;
  let createHoverElementAction;

  setupPromiseMocking();

  beforeEach(() => {
    generateHeatmap = sinon.stub().returns(heatmap);
    __set__('generateHeatmap', generateHeatmap);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createHoverElementAction = sinon.stub().returns(HOVER_ACTION);
    __set__('createHoverElementAction', createHoverElementAction);

    clearFunction = sinon.spy();

    addFunction = sinon.spy();

    diagramGraphics = document.createElement('div');
    diagramGraphics.innerHTML = '<div class="djs-hit" width="20"></div>';

    viewer = {
      get: sinon.stub().returns({
        clear: clearFunction,
        getGraphics: sinon.stub().returns(diagramGraphics),
        add: addFunction
      })
    };
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('generateHeatmap');
    __ResetDependency__('createHoverElementAction');
  });

  describe('get Heatmap', () => {
    let response;

    beforeEach(() => {
      response = getHeatmap(viewer, heatmapData);
    });

    it('should return an svg image node containing the heatmap', () => {
      expect(response instanceof SVGImageElement).to.eql(true);
      expect(response.href.baseVal).to.eql(heatmap.img);
    });

    it('should set the width and height properties of the returned image', () => {
      expect(response.getAttribute('height')).to.eql(heatmap.dimensions.height.toString());
      expect(response.getAttribute('width')).to.eql(heatmap.dimensions.width.toString());
    });
  });

  describe('hover overlays', () => {
    it('should dispatch hover action', () => {
      hoverElement(diagramElement);

      expect(dispatchAction.calledWith(HOVER_ACTION)).to.eql(true);
    });

    it('should remove overlays', () => {
      removeHeatmapOverlay(viewer);

      expect(clearFunction.calledOnce).to.eql(true);
    });

    it('should add an overlay on the specified element', () => {
      addHeatmapOverlay(viewer, heatmapData, diagramElement.id);

      expect(addFunction.calledWith(diagramElement.id)).to.eql(true);
    });

    it('should add an overlay with the heat value as text content', () => {
      addHeatmapOverlay(viewer, heatmapData, diagramElement.id);

      const node = addFunction.getCall(0).args[1].html;

      expect(node.textContent.trim()).to.eql(heatmapData[diagramElement.id].toString());
    });

    it('should not add an overlay if specified element has no heat data', () => {
      addHeatmapOverlay(viewer, heatmapData, 'nonExistent');

      expect(addFunction.called).to.eql(false);
    });
  });
});
