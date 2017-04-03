import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadProcessDefinitions, openDefinition, setVersionForProcess,
        __set__, __ResetDependency__} from 'main/processSelection/service';

describe('ProcessSelection service', () => {
  const START_ACTION = 'START_ACTION';
  const STOP_ACTION = 'STOP_ACTION';
  const SET_ACTION = 'SET_ACTION';
  const processDefinitionResponse = 'some process definition data';

  let router;
  let dispatchAction;
  let get;
  let createLoadProcessDefinitionsAction;
  let createLoadProcessDefinitionsResultAction;
  let createSetVersionAction;

  setupPromiseMocking();

  beforeEach(() => {
    get = sinon.stub().returns(Promise.resolve({
      json: sinon.stub().returns(
        Promise.resolve(processDefinitionResponse)
      )
    }));
    __set__('get', get);

    router = {
      goTo: sinon.spy()
    };
    __set__('router', router);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createLoadProcessDefinitionsAction = sinon.stub().returns(START_ACTION);
    __set__('createLoadProcessDefinitionsAction', createLoadProcessDefinitionsAction);

    createLoadProcessDefinitionsResultAction = sinon.stub().returns(STOP_ACTION);
    __set__('createLoadProcessDefinitionsResultAction', createLoadProcessDefinitionsResultAction);

    createSetVersionAction = sinon.stub().returns(SET_ACTION);
    __set__('createSetVersionAction', createSetVersionAction);
  });

  afterEach(() => {
    __ResetDependency__('get');
    __ResetDependency__('router');
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createLoadProcessDefinitionsAction');
    __ResetDependency__('createLoadProcessDefinitionsResultAction');
    __ResetDependency__('createSetVersionAction');
  });

  describe('loadProcessDefinitions', () => {
    beforeEach(() => {
      loadProcessDefinitions();
      Promise.runAll();
    });

    it('should dispatch event when loading data', () => {
      expect(createLoadProcessDefinitionsAction.calledOnce).to.eql(true);
      expect(dispatchAction.calledWith(START_ACTION)).to.eql(true);
    });

    it('should call the backend', () => {
      expect(get.calledOnce).to.eql(true);
    });

    it('should dispatch an action with the returned response', () => {
      expect(dispatchAction.calledWith(STOP_ACTION)).to.eql(true);
      expect(createLoadProcessDefinitionsResultAction.calledWith(processDefinitionResponse)).to.eql(true);
    });
  });

  describe('openDefinition', () => {
    it('should go to the process display route', () => {
      openDefinition('a');

      expect(router.goTo.calledWith('processDisplay', {definition: 'a'})).to.eql(true);
    });
  });

  describe('setVersionForProcess', () => {
    it('should create an action with the arguments', () => {
      setVersionForProcess('a', 1);
      expect(createSetVersionAction.calledWith('a', 1));
    });

    it('should dispatch the action', () => {
      setVersionForProcess('a', 1);
      expect(dispatchAction.calledWith(SET_ACTION));
    });
  });
});
