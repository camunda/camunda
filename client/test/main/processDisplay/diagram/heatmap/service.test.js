import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {getHeatmap, hoverElement, addHeatmapOverlay,
        VALUE_OVERLAY, __set__, __ResetDependency__} from 'main/processDisplay/diagram/heatmap/service';

describe('Heatmap service', () => {
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
  let notHoveredOverlay;
  let hoveredOverlay;
  let getFunction;

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

    notHoveredOverlay = document.createElement('div');
    hoveredOverlay = document.createElement('div');

    getFunction = sinon.stub();
    getFunction.onFirstCall().returns([{
      html: notHoveredOverlay
    }]);
    getFunction.returns([{
      html: hoveredOverlay
    }]);

    viewer = {
      get: sinon.stub().returns({
        clear: clearFunction,
        getGraphics: sinon.stub().returns(diagramGraphics),
        add: addFunction,
        get: getFunction
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
    it('should add overlays on the elements', () => {
      addHeatmapOverlay(viewer, heatmapData);

      expect(addFunction.calledWith(diagramElement.id)).to.eql(true);
    });

    it('should add an overlay with the heat value as text content', () => {
      addHeatmapOverlay(viewer, heatmapData);

      const node = addFunction.getCall(0).args[2].html;

      expect(node.textContent.trim()).to.eql(heatmapData[diagramElement.id].toString());
    });

    it('should add the overlay with the correct type', () => {
      addHeatmapOverlay(viewer, heatmapData);

      const type = addFunction.getCall(0).args[1];

      expect(type).to.eql(VALUE_OVERLAY);
    });

    describe('interaction', () => {
      it('should clear the opacity of all overlays', () => {
        hoverElement(viewer, 'someElement');

        expect(notHoveredOverlay.style.opacity).to.eql('0');
      });

      it('should set the opacity of the hovered element', () => {
        hoverElement(viewer, 'someElement');

        expect(hoveredOverlay.style.opacity).to.eql('1');
      });
    });
  });
});
