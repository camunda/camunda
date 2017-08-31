import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadHeatmap, loadDiagram, loadData, getDefinitionId, loadTargetValue,
        __set__, __ResetDependency__} from 'main/processDisplay/service';

describe('ProcessDisplay service', () => {
  const START_ACTION_DIAGRAM = 'START_LOADING_DIAGRAM';
  const STOP_ACTION_DIAGRAM = 'STOP_LOADING_DIAGRAM';
  const START_ACTION_HEATMAP = 'START_LOADING_HEATMAP';
  const STOP_ACTION_HEATMAP = 'STOP_LOADING_HEATMAP';
  const START_ACTION_TARGET_VALUE = 'START_LOADING_TARGET_VALUE';
  const STOP_ACTION_TARGET_VALUE = 'STOP_LOADING_TARGET_VALUE';

  const xml = 'some process xml';

  const processId = 'procId';

  let getLastRoute;
  let dispatchAction;
  let createLoadingDiagramAction;
  let createLoadingDiagramResultAction;
  let createLoadingHeatmapAction;
  let createLoadingHeatmapResultAction;
  let createLoadingTargetValueAction;
  let createLoadingTargetValueResultAction;
  let heatmapData;
  let targetValueData;
  let addNotification;
  let filter;
  let get;
  let post;
  let router;

  setupPromiseMocking();

  beforeEach(() => {
    heatmapData = {flowNodes: {
      act1: 2,
      act2: 8
    }};

    targetValueData = {targetValues: {
      act1: 5,
      act2: 5
    }};

    filter = {
      processDefinitionId: processId
    };

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    addNotification = sinon.spy();
    __set__('addNotification', addNotification);

    createLoadingDiagramAction = sinon.stub().returns(START_ACTION_DIAGRAM);
    __set__('createLoadingDiagramAction', createLoadingDiagramAction);

    createLoadingDiagramResultAction = sinon.stub().returns(STOP_ACTION_DIAGRAM);
    __set__('createLoadingDiagramResultAction', createLoadingDiagramResultAction);

    createLoadingHeatmapAction = sinon.stub().returns(START_ACTION_HEATMAP);
    __set__('createLoadingHeatmapAction', createLoadingHeatmapAction);

    createLoadingHeatmapResultAction = sinon.stub().returns(STOP_ACTION_HEATMAP);
    __set__('createLoadingHeatmapResultAction', createLoadingHeatmapResultAction);

    createLoadingTargetValueAction = sinon.stub().returns(START_ACTION_TARGET_VALUE);
    __set__('createLoadingTargetValueAction', createLoadingTargetValueAction);

    createLoadingTargetValueResultAction = sinon.stub().returns(STOP_ACTION_TARGET_VALUE);
    __set__('createLoadingTargetValueResultAction', createLoadingTargetValueResultAction);

    getLastRoute = sinon.stub().returns({
      params: {
        definition: processId
      }
    });
    __set__('getLastRoute', getLastRoute);

    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('addNotification');
    __ResetDependency__('createLoadingDiagramAction');
    __ResetDependency__('createLoadingDiagramResultAction');
    __ResetDependency__('createLoadingHeatmapAction');
    __ResetDependency__('createLoadingHeatmapResultAction');
    __ResetDependency__('createLoadingTargetValueAction');
    __ResetDependency__('createLoadingTargetValueResultAction');
    __ResetDependency__('getLastRoute');
    __ResetDependency__('router');
  });

  describe('loadData', () => {
    let loadHeatmap;
    let loadDiagram;
    let loadTargetValue;

    beforeEach(() => {
      loadHeatmap = sinon.spy();
      loadDiagram = sinon.spy();
      loadTargetValue = sinon.spy();

      __set__('loadHeatmap', loadHeatmap);
      __set__('loadDiagram', loadDiagram);
      __set__('loadTargetValue', loadTargetValue);
    });

    afterEach(() => {
      __ResetDependency__('loadHeatmap');
      __ResetDependency__('loadDiagram');
      __ResetDependency__('loadTargetValue');
    });

    it('should load heatmap when view is duration or frequency', () => {
      loadData({
        definition: 'some-id',
        query: [],
        view: 'duration'
      });

      loadData({
        definition: 'some-id',
        query: [],
        view: 'frequency'
      });

      expect(loadHeatmap.callCount).to.eql(2, 'expected heatmap to be loaded');
    });

    it('should load heatmap with correct process definition and filter', () => {
      const start = 'start-date';
      const end = 'end-date';
      const expectedQuery = {
        processDefinitionId: processId,
        filter: {
          dates: [
            {
              type: 'start_date',
              operator: '>=',
              value : start,
              lowerBoundary : true,
              upperBoundary : true
            },
            {
              type: 'start_date',
              operator: '<=',
              value : end,
              lowerBoundary : true,
              upperBoundary : true
            }
          ],
          variables: [],
          executedFlowNodes: []
        }
      };

      loadData({
        query: [
          {
            type: 'startDate',
            data: {
              start,
              end
            }
          }
        ],
        view: 'duration'
      });

      expect(loadHeatmap.calledWith('duration', expectedQuery)).to.eql(true, 'expected heatmap to be loaded with correct query');
    });

    it('should load target value when view is target_value', () => {
      loadData({
        query: [],
        view: 'target_value'
      });

      expect(loadTargetValue.calledWith(processId)).to.eql(true);
    });
  });

  describe('load target value', () => {
    beforeEach(() => {
      get = sinon.stub().returns(Promise.resolve({
        json: sinon.stub().returns(
          Promise.resolve(targetValueData)
        )
      }));
      __set__('get', get);

      loadTargetValue(processId);

      Promise.runAll();
    });

    afterEach(() => {
      __ResetDependency__('get');
    });

    it('should create and dispatch a result action with response from backend', () => {
      expect(createLoadingTargetValueResultAction.calledWith(targetValueData.targetValues)).to.eql(true);
      expect(dispatchAction.calledWith(STOP_ACTION_TARGET_VALUE)).to.eql(true);
    });
  });

  describe('load target value failure', () => {
    beforeEach(() => {
      get = sinon.stub().returns(Promise.reject());
      __set__('get', get);

      loadTargetValue(processId);

      Promise.runAll();
    });

    afterEach(() => {
      __ResetDependency__('get');
    });

    it('should show an error message', () => {
      expect(addNotification.calledOnce).to.eql(true);
      expect(addNotification.firstCall.args[0].isError).to.eql(true);
    });
  });

  describe('load heatmap', () => {
    beforeEach(() => {
      post = sinon.stub().returns(Promise.resolve({
        json: sinon.stub().returns(
          Promise.resolve(heatmapData)
        )
      }));
      __set__('post', post);

      loadHeatmap('frequency', filter);
    });

    afterEach(() => {
      __ResetDependency__('post');
    });

    it('dispatches start loading action', () => {
      expect(dispatchAction.calledWith(START_ACTION_HEATMAP)).to.eql(true);
    });

    it('calls backend with specified filter for frequency', () => {
      expect(post.calledWith('/api/process-definition/heatmap/frequency', filter)).to.eql(true);
    });

    it('calls backend with specified filter for duration', () => {
      loadHeatmap('duration', filter);

      expect(post.calledWith('/api/process-definition/heatmap/duration', filter)).to.eql(true);
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

  describe('load heatmap failure', () => {
    const ERROR_MSG ='I_AM_ERROR';
    const ERROR_ACTION = 'ERROR_ACTION';

    let createLoadingHeatmapErrorAction;

    beforeEach(() => {
      post = sinon.stub().returns(Promise.reject(ERROR_MSG));
      __set__('post', post);

      createLoadingHeatmapErrorAction = sinon.stub().returns(ERROR_ACTION);
      __set__('createLoadingHeatmapErrorAction', createLoadingHeatmapErrorAction);

      loadHeatmap('frequency', filter);
      Promise.runAll();
    });

    afterEach(() => {
      __ResetDependency__('createLoadingHeatmapErrorAction');
    });

    it('should show an error notification', () => {
      expect(addNotification.calledOnce).to.eql(true);
    });

    it('should create and dispatch a loading error action', () => {
      expect(createLoadingHeatmapErrorAction.calledOnce).to.eql(true);
      expect(createLoadingHeatmapErrorAction.calledWith(ERROR_MSG)).to.eql(true);
      expect(dispatchAction.calledWith(ERROR_ACTION)).to.eql(true);
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

      loadDiagram();
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

  describe('load diagram failure', () => {
    const ERROR_MSG ='I_AM_ERROR';
    const ERROR_ACTION = 'ERROR_ACTION';

    let createLoadingDiagramErrorAction;

    beforeEach(() => {
      get = sinon.stub().returns(Promise.reject(ERROR_MSG));
      __set__('get', get);

      createLoadingDiagramErrorAction = sinon.stub().returns(ERROR_ACTION);
      __set__('createLoadingDiagramErrorAction', createLoadingDiagramErrorAction);

      loadDiagram();
      Promise.runAll();
    });

    afterEach(() => {
      __ResetDependency__('createLoadingDiagramErrorAction');
    });

    it('should show an error notification', () => {
      expect(addNotification.calledOnce).to.eql(true);
    });

    it('should create and dispatch a loading error action', () => {
      expect(createLoadingDiagramErrorAction.calledOnce).to.eql(true);
      expect(createLoadingDiagramErrorAction.calledWith(ERROR_MSG)).to.eql(true);
      expect(dispatchAction.calledWith(ERROR_ACTION)).to.eql(true);
    });

    it('should redirect to default view', () => {
      expect(router.goTo.calledWith('default')).to.eql(true);
    });
  });

  describe('getDefinitionId', () => {
    it('should retrieve the process definition from the url', () => {
      const id = getDefinitionId();

      expect(id).to.eql(processId);
      expect(getLastRoute.calledOnce).to.eql(true);
    });
  });
});
