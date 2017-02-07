import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadHeatmap, loadDiagram,
        __set__, __ResetDependency__} from 'main/processDisplay/service';

describe('ProcessDefinition service', () => {
  const START_ACTION_DIAGRAM = 'START_LOADING_DIAGRAM';
  const STOP_ACTION_DIAGRAM = 'STOP_LOADING_DIAGRAM';
  const START_ACTION_HEATMAP = 'START_LOADING_HEATMAP';
  const STOP_ACTION_HEATMAP = 'STOP_LOADING_HEATMAP';

  const xml = 'some process xml';

  const processId = 'procId';

  let dispatchAction;
  let createLoadingDiagramAction;
  let createLoadingDiagramResultAction;
  let createLoadingHeatmapAction;
  let createLoadingHeatmapResultAction;
  let heatmapData;
  let filter;
  let get;

  setupPromiseMocking();

  beforeEach(() => {
    heatmapData = {
      act1: 2,
      act2: 8
    };

    filter = {
      id: processId
    };

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createLoadingDiagramAction = sinon.stub().returns(START_ACTION_DIAGRAM);
    __set__('createLoadingDiagramAction', createLoadingDiagramAction);

    createLoadingDiagramResultAction = sinon.stub().returns(STOP_ACTION_DIAGRAM);
    __set__('createLoadingDiagramResultAction', createLoadingDiagramResultAction);

    createLoadingHeatmapAction = sinon.stub().returns(START_ACTION_HEATMAP);
    __set__('createLoadingHeatmapAction', createLoadingHeatmapAction);

    createLoadingHeatmapResultAction = sinon.stub().returns(STOP_ACTION_HEATMAP);
    __set__('createLoadingHeatmapResultAction', createLoadingHeatmapResultAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createLoadingDiagramAction');
    __ResetDependency__('createLoadingDiagramResultAction');
    __ResetDependency__('createLoadingHeatmapAction');
    __ResetDependency__('createLoadingHeatmapResultAction');
  });

  describe('load heatmap', () => {
    beforeEach(() => {
      get = sinon.stub().returns(Promise.resolve({
        json: sinon.stub().returns(
          Promise.resolve(heatmapData)
        )
      }));
      __set__('get', get);

      loadHeatmap(filter);
    });

    afterEach(() => {
      __ResetDependency__('get');
    });

    it('dispatches start loading action', () => {
      expect(dispatchAction.calledWith(START_ACTION_HEATMAP)).to.eql(true);
    });

    it('calls backend', () => {
      expect(get.calledWith('/api/process-definition/' + processId + '/heatmap')).to.eql(true);
    });

    it('dispatches stop loading action', () => {
      Promise.runAll();

      expect(dispatchAction.calledWith(STOP_ACTION_HEATMAP)).to.eql(true);
    });

    it('creates action with returned heatmap data', () => {
      Promise.runAll();

      expect(createLoadingHeatmapResultAction.calledWith(heatmapData)).to.eql(true);
    });
  });

  describe('load diagram', () => {
    beforeEach(() => {
      get = sinon.stub().returns(Promise.resolve({
        text: sinon.stub().returns(
          Promise.resolve(xml)
        )
      }));
      __set__('get', get);

      loadDiagram(filter);
    });

    afterEach(() => {
      __ResetDependency__('get');
    });

    it('dispatches start loading action', () => {
      expect(dispatchAction.calledWith(START_ACTION_DIAGRAM)).to.eql(true);
    });

    it('calls backend', () => {
      expect(get.calledWith('/api/process-definition/' + processId + '/xml')).to.eql(true);
    });

    it('dispatches stop loading action', () => {
      Promise.runAll();

      expect(dispatchAction.calledWith(STOP_ACTION_DIAGRAM)).to.eql(true);
    });

    it('creates action with returned heatmap data', () => {
      Promise.runAll();

      expect(createLoadingDiagramResultAction.calledWith(xml)).to.eql(true);
    });
  });
});
