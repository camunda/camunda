import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {getHeatmap, loadHeatmap, loadDiagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/diagram.service';

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
  const viewer = {};
  const diagramXml = 'diagram-xml';
  const diagram = {id: 'aDiagramId'};

  const PROCESS_DIAGRAM_ACTION = 'LOAD_PROCESS_DIAGRAM';
  const PROCESS_DIAGRAM_RESULT_ACTION = 'LOAD_PROCESS_DIAGRAM_RESULT';
  const HEATMAP_ACTION = 'LOAD_HEATMAP';
  const HEATMAP_RESULT_ACTION = 'LOAD_HEATMAP_RESULT';

  let generateHeatmap,
      getHeatmapData,
      getDiagramXml,
      dispatchAction,
      createLoadingDiagramAction,
      createLoadingHeatmapAction,
      createLoadingDiagramResultAction,
      createLoadingHeatmapResultAction;

  setupPromiseMocking();

  beforeEach(() => {
    generateHeatmap = sinon.stub().returns(heatmap);
    __set__('generateHeatmap', generateHeatmap);

    getHeatmapData = sinon.stub().returns(Promise.resolve(heatmapData));
    __set__('getHeatmapData', getHeatmapData);

    getDiagramXml = sinon.stub().returns(Promise.resolve(diagramXml));
    __set__('getDiagramXml', getDiagramXml);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createLoadingDiagramAction = sinon.stub().returns(PROCESS_DIAGRAM_ACTION);
    __set__('createLoadingDiagramAction', createLoadingDiagramAction);

    createLoadingHeatmapAction = sinon.stub().returns(HEATMAP_ACTION);
    __set__('createLoadingHeatmapAction', createLoadingHeatmapAction);

    createLoadingDiagramResultAction = sinon.stub().returns(PROCESS_DIAGRAM_RESULT_ACTION);
    __set__('createLoadingDiagramResultAction', createLoadingDiagramResultAction);

    createLoadingHeatmapResultAction = sinon.stub().returns(HEATMAP_RESULT_ACTION);
    __set__('createLoadingHeatmapResultAction', createLoadingHeatmapResultAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('generateHeatmap');
    __ResetDependency__('getHeatmapData');
    __ResetDependency__('getDiagramXml');
    __ResetDependency__('createLoadingDiagramAction');
    __ResetDependency__('createLoadingHeatmapAction');
    __ResetDependency__('createLoadingDiagramResultAction');
    __ResetDependency__('createLoadingHeatmapResultAction');
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

  describe('load Diagram', () => {
    beforeEach(() => {
      loadDiagram(diagram);
    });

    it('should dispatch loading action', () => {
      expect(dispatchAction.calledWith(PROCESS_DIAGRAM_ACTION)).to.eql(true);
    });

    it('should load the diagram xml', () => {
      expect(getDiagramXml.calledOnce).to.eql(true);
    });

    it('should dispatch action with response content', () => {
      Promise.runAll();
      expect(dispatchAction.calledWith(PROCESS_DIAGRAM_RESULT_ACTION)).to.eql(true);
      expect(createLoadingDiagramResultAction.calledWith(diagramXml)).to.eql(true);
    });
  });

  describe('load heatmap', () => {
    beforeEach(() => {
      loadHeatmap(diagram);
    });

    it('should dispatch loading action', () => {
      expect(dispatchAction.calledWith(HEATMAP_ACTION)).to.eql(true);
    });

    it('should load the heatmap data', () => {
      expect(getHeatmapData.calledOnce).to.eql(true);
    });

    it('should dispatch action with response content', () => {
      Promise.runAll();
      expect(dispatchAction.calledWith(HEATMAP_RESULT_ACTION)).to.eql(true);
      expect(createLoadingHeatmapResultAction.calledWith(heatmapData)).to.eql(true);
    });
  });
});
