import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadProgress, __set__, __ResetDependency__} from 'main/footer/progress/service';

describe('loadProgress', () => {
  let progressResponse;
  let connectionResponse;
  let get;
  let dispatchAction;
  let addNotification;
  let cancelTask;
  let interval;
  let createLoadingProgressAction;
  let createLoadingProgressResultAction;
  let createLoadingProgressErrorAction;

  setupPromiseMocking();

  beforeEach(() => {
    progressResponse = {
      json: sinon.stub().returnsThis(),
      progress: 20
    };

    connectionResponse = {
      json: sinon.stub().returnsThis(),
      a: 1
    };

    get = sinon.stub();
    __set__('get', get);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    addNotification = sinon.spy();
    __set__('addNotification', addNotification);

    cancelTask = sinon.spy();
    interval = sinon.stub().returns(cancelTask);
    __set__('interval', interval);

    createLoadingProgressAction = sinon.stub();
    createLoadingProgressAction.returns(createLoadingProgressAction);
    __set__('createLoadingProgressAction', createLoadingProgressAction);

    createLoadingProgressResultAction = sinon.stub();
    createLoadingProgressResultAction.returns(createLoadingProgressResultAction);
    __set__('createLoadingProgressResultAction', createLoadingProgressResultAction);

    createLoadingProgressErrorAction = sinon.stub();
    createLoadingProgressErrorAction.returns(createLoadingProgressErrorAction);
    __set__('createLoadingProgressErrorAction', createLoadingProgressErrorAction);
  });

  afterEach(() => {
    __ResetDependency__('get');
    __ResetDependency__('dispatchAction');
    __ResetDependency__('addNotification');
    __ResetDependency__('interval');
    __ResetDependency__('createLoadingProgressAction');
    __ResetDependency__('createLoadingProgressResultAction');
    __ResetDependency__('createLoadingProgressErrorAction');
  });

  describe('on success', () => {
    beforeEach(() => {
      get.withArgs('/api/status/import-progress').returns(Promise.resolve(progressResponse));
      get.withArgs('/api/status/connection').returns(Promise.resolve(connectionResponse));

      loadProgress();
    });

    it('should dispatch loading action', () => {
      expect(dispatchAction.calledWith(createLoadingProgressAction)).to.eql(true);
    });

    it('should fetch GET import-progress API endpoint', () => {
      expect(get.calledWith('/api/status/import-progress')).to.eql(true);
    });

    it('should fetch GET connection status API endpoint', () => {
      expect(get.calledWith('/api/status/connection')).to.eql(true);
    });

    it('should start interval', () => {
      expect(interval.called).to.eql(true);
    });

    it('should cancel interval when progress is 100', () => {
      progressResponse.progress = 100;

      Promise.runAll();

      expect(cancelTask.calledOnce).to.eql(true);
    });

    it('should not cancel task when progress is below 100', () => {
      Promise.runAll();

      expect(cancelTask.called).to.eql(false);
    });

    it('should dispatch progress result action', () => {
      Promise.runAll();

      expect(createLoadingProgressResultAction.calledWith({
        ...progressResponse,
        ...connectionResponse
      })).to.eql(true);
      expect(dispatchAction.calledWith(createLoadingProgressResultAction)).to.eql(true);
    });
  });

  describe('on error', () => {
    beforeEach(() => {
      get.returns(Promise.reject(progressResponse));
      loadProgress();
      Promise.runAll();
    });

    it('should dispatch login error action', () => {
      expect(dispatchAction.calledWith(createLoadingProgressErrorAction)).to.eql(true);
    });

    it('should cancel task', () => {
      expect(cancelTask.calledOnce).to.eql(true);
    });

    it('should add notification', () => {
      expect(addNotification.called).to.eql(true);
    });
  });
});
