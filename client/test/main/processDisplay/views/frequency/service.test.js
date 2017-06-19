import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {getHeatmap, addHeatmapOverlay, __set__, __ResetDependency__} from 'main/processDisplay/views/frequency/service';

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
  let formatter;
  let addDiagramTooltip;

  setupPromiseMocking();

  beforeEach(() => {
    generateHeatmap = sinon.stub().returns(heatmap);
    __set__('generateHeatmap', generateHeatmap);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createHoverElementAction = sinon.stub().returns(HOVER_ACTION);
    __set__('createHoverElementAction', createHoverElementAction);

    formatter = sinon.stub().returns(document.createTextNode('0'));

    clearFunction = sinon.spy();

    addDiagramTooltip = sinon.spy();
    __set__('addDiagramTooltip', addDiagramTooltip);

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
    __ResetDependency__('addDiagramTooltip');
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
    it('should add diagram tooltip', () => {
      addHeatmapOverlay(viewer, diagramElement.id, heatmapData, formatter);

      expect(addDiagramTooltip.calledWith(viewer, diagramElement.id)).to.eql(true);
    });

    it('should add an overlay with the heat value as text content', () => {
      const overlayValue = document.createTextNode(heatmapData[diagramElement.id].toString());

      formatter.returns(overlayValue);

      addHeatmapOverlay(viewer, diagramElement.id, heatmapData, formatter);

      expect(addDiagramTooltip.calledWith(viewer, diagramElement.id, overlayValue)).to.eql(true);
    });
  });
});
