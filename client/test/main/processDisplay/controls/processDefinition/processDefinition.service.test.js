import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadProcessDefinitions, selectProcessDefinition,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/processDefinition/processDefinition.service';

describe('ProcessDefinition service', () => {
  const SELECT_ACTION = 'SELECT_PROCESS_DEFINITION';
  const START_ACTION = 'START_LOADING';
  const STOP_ACTION = 'STOP_LOADING';

  const processes = [
    {id: 'id1', name: 'name1'},
    {id: 'id2', name: 'name2'}
  ];

  let dispatchAction;
  let createLoadProcessDefinitionsAction;
  let createSelectProcessDefinitionAction;
  let createLoadProcessDefinitionsResultAction;
  let get;

  setupPromiseMocking();

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    createSelectProcessDefinitionAction = sinon.stub().returns(SELECT_ACTION);
    __set__('createSelectProcessDefinitionAction', createSelectProcessDefinitionAction);

    createLoadProcessDefinitionsAction = sinon.stub().returns(START_ACTION);
    __set__('createLoadProcessDefinitionsAction', createLoadProcessDefinitionsAction);

    createLoadProcessDefinitionsResultAction = sinon.stub().returns(STOP_ACTION);
    __set__('createLoadProcessDefinitionsResultAction', createLoadProcessDefinitionsResultAction);

    get = sinon.stub().returns(Promise.resolve({
      json: sinon.stub().returns(
        Promise.resolve(processes)
      )
    }));
    __set__('get', get);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createLoadProcessDefinitionsAction');
    __ResetDependency__('createSelectProcessDefinitionAction');
    __ResetDependency__('createLoadProcessDefinitionsResultAction');
    __ResetDependency__('get');
  });

  describe('select process definition', () => {
    const id = 'someProcessId';

    beforeEach(() => {
      selectProcessDefinition(id);
    });
    it('should dispatch select process action', () => {
      expect(dispatchAction.calledWith(SELECT_ACTION)).to.eql(true);
    });

    it('should create action with provided process definition id', () => {
      expect(createSelectProcessDefinitionAction.calledWith(id)).to.eql(true);
    });
  });

  describe('load process definitions', () => {
    beforeEach(() => {
      loadProcessDefinitions();
    });

    it('dispatches start loading action', () => {
      expect(dispatchAction.calledWith(START_ACTION)).to.eql(true);
    });

    it('calls backend', () => {
      expect(get.calledWith('/api/process-definition')).to.eql(true);
    });

    it('dispatches stop loading action', () => {
      Promise.runAll();

      expect(dispatchAction.calledWith(STOP_ACTION)).to.eql(true);
    });

    it('creates action with returned process list', () => {
      Promise.runAll();

      expect(createLoadProcessDefinitionsResultAction.calledWith(processes)).to.eql(true);
    });
  });
});
