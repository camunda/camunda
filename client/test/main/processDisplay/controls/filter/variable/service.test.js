import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadVariables, createVariableFilter, selectVariableIdx, deselectVariableIdx, setOperator, setValue,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/variable/service';

describe('Variable Filter service', () => {
  const CREATE_FILTER_ACTION = 'CREATE_FILTER_ACTION';
  const SELECT_VARIABLE_ACTION = 'SELECT_VARIABLE_ACTION';
  const SET_OPERATOR_ACTION = 'SET_OPERATOR_ACTION';
  const SET_VALUE_ACTION = 'SET_VALUE_ACTION';
  const START_LOADING_ACTION = 'START_LOADING_ACTION';
  const LOADING_RESULT_ACTION = 'LOADING_RESULT_ACTION';
  const LOADING_ERROR_ACTION = 'LOADING_ERROR_ACTION';

  let variablesData;

  let dispatch;
  let dispatchAction;
  let get;
  let createLoadingVariablesAction;
  let createLoadingVariablesResultAction;
  let createLoadingVariablesErrorAction;
  let createSelectVariableIdxAction;
  let createSetOperatorAction;
  let createSetValueAction;
  let createCreateVariableFilterAction;
  let addNotification;

  setupPromiseMocking();

  beforeEach(() => {
    variablesData = [{name: 'b'}, {name: 'a'}];

    dispatch = sinon.spy();
    __set__('dispatch', dispatch);

    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    get = sinon.stub().returns(Promise.resolve({
      json: sinon.stub().returns(
        Promise.resolve(variablesData)
      )
    }));
    __set__('get', get);

    addNotification = sinon.spy();
    __set__('addNotification', addNotification);

    createLoadingVariablesAction = sinon.stub().returns(START_LOADING_ACTION);
    __set__('createLoadingVariablesAction', createLoadingVariablesAction);

    createLoadingVariablesResultAction = sinon.stub().returns(LOADING_RESULT_ACTION);
    __set__('createLoadingVariablesResultAction', createLoadingVariablesResultAction);

    createSelectVariableIdxAction = sinon.stub().returns(SELECT_VARIABLE_ACTION);
    __set__('createSelectVariableIdxAction', createSelectVariableIdxAction);

    createLoadingVariablesErrorAction = sinon.stub().returns(LOADING_ERROR_ACTION);
    __set__('createLoadingVariablesErrorAction', createLoadingVariablesErrorAction);

    createSetOperatorAction = sinon.stub().returns(SET_OPERATOR_ACTION);
    __set__('createSetOperatorAction', createSetOperatorAction);

    createSetValueAction = sinon.stub().returns(SET_VALUE_ACTION);
    __set__('createSetValueAction', createSetValueAction);

    createCreateVariableFilterAction = sinon.stub().returns(CREATE_FILTER_ACTION);
    __set__('createCreateVariableFilterAction', createCreateVariableFilterAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatch');
    __ResetDependency__('dispatchAction');
    __ResetDependency__('get');
    __ResetDependency__('addNotification');
    __ResetDependency__('createLoadingVariablesAction');
    __ResetDependency__('createLoadingVariablesResultAction');
    __ResetDependency__('createSelectVariableIdxAction');
    __ResetDependency__('createLoadingVariablesErrorAction');
    __ResetDependency__('createSetOperatorAction');
    __ResetDependency__('createSetValueAction');
    __ResetDependency__('createCreateVariableFilterAction');
  });

  describe('loadVariables', () => {
    const definition = 'definition';

    it('should dispatch a start loading action', () => {
      loadVariables(definition);

      expect(createLoadingVariablesAction.calledOnce).to.eql(true);
      expect(dispatchAction.calledWith(START_LOADING_ACTION)).to.eql(true);
    });

    it('should call the backend', () => {
      loadVariables(definition);

      expect(get.calledOnce).to.eql(true);
      expect(get.getCall(0).args[0]).to.include(definition);
    });

    it('should dispatch an action with the returned result', () => {
      loadVariables(definition);

      Promise.runAll();

      expect(createLoadingVariablesResultAction.calledWith(variablesData)).to.eql(true);
      expect(dispatchAction.calledWith(LOADING_RESULT_ACTION)).to.eql(true);
    });

    it('should sort the variables by name', () => {
      loadVariables(definition);

      Promise.runAll();

      expect(variablesData[0].name).to.eql('a');
      expect(variablesData[1].name).to.eql('b');
    });

    it('should show a notification in case anything goes wrong', () => {
      get.returns(Promise.reject('NOPE'));
      loadVariables(definition);

      Promise.runAll();

      expect(addNotification.calledOnce).to.eql(true);
      expect(addNotification.firstCall.args[0].isError).to.eql(true);
    });

    it('should dispatch an error action in case anything goes wrong', () => {
      get.returns(Promise.reject('NOPE'));
      loadVariables(definition);

      Promise.runAll();

      expect(createLoadingVariablesErrorAction.calledWith('NOPE')).to.eql(true);
      expect(dispatchAction.calledWith(LOADING_ERROR_ACTION)).to.eql(true);
    });
  });

  describe('createVariableFilter', () => {
    const filter = 'some filter';

    beforeEach(() => {
      createVariableFilter(filter);
    });

    it('should create action', () => {
      expect(createCreateVariableFilterAction.calledOnce).to.eql(true);
      expect(createCreateVariableFilterAction.calledWith(filter)).to.eql(true);
    });

    it('should dispatch action', () => {
      expect(dispatch.calledWith(CREATE_FILTER_ACTION)).to.eql(true);
    });
  });

  describe('selectVariableIdx', () => {
    const idx = 'some idx';

    beforeEach(() => {
      selectVariableIdx(idx);
    });

    it('should create action', () => {
      expect(createSelectVariableIdxAction.calledOnce).to.eql(true);
      expect(createSelectVariableIdxAction.calledWith(idx)).to.eql(true);
    });

    it('should dispatch action', () => {
      expect(dispatchAction.calledWith(SELECT_VARIABLE_ACTION)).to.eql(true);
    });
  });

  describe('deselectVariableIdx', () => {
    beforeEach(() => {
      deselectVariableIdx();
    });

    it('should create action', () => {
      expect(createSelectVariableIdxAction.calledOnce).to.eql(true);
      expect(createSelectVariableIdxAction.calledWith(undefined)).to.eql(true);
    });

    it('should dispatch action', () => {
      expect(dispatchAction.calledWith(SELECT_VARIABLE_ACTION)).to.eql(true);
    });
  });

  describe('setOperator', () => {
    const operator = 'some operator';

    beforeEach(() => {
      setOperator(operator);
    });

    it('should create action', () => {
      expect(createSetOperatorAction.calledOnce).to.eql(true);
      expect(createSetOperatorAction.calledWith(operator)).to.eql(true);
    });

    it('should dispatch action', () => {
      expect(dispatchAction.calledWith(SET_OPERATOR_ACTION)).to.eql(true);
    });
  });

  describe('setValue', () => {
    const value = 'some value';

    beforeEach(() => {
      setValue(value);
    });

    it('should create action', () => {
      expect(createSetValueAction.calledOnce).to.eql(true);
      expect(createSetValueAction.calledWith(value)).to.eql(true);
    });

    it('should dispatch action', () => {
      expect(dispatchAction.calledWith(SET_VALUE_ACTION)).to.eql(true);
    });
  });
});
