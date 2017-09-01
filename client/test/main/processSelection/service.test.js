import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {loadProcessDefinitions, openDefinition, setVersionForProcess,
        __set__, __ResetDependency__} from 'main/processSelection/service';

describe('ProcessSelection service', () => {
  const START_ACTION = 'START_ACTION';
  const STOP_ACTION = 'STOP_ACTION';
  const SET_ACTION = 'SET_ACTION';

  let router;
  let dispatchAction;
  let get;
  let createLoadProcessDefinitionsAction;
  let createLoadProcessDefinitionsResultAction;
  let createSetVersionAction;
  let processDefinitionResponse;
  let xmlResponse;

  setupPromiseMocking();

  beforeEach(() => {
    get = sinon.stub();
    __set__('get', get);

    processDefinitionResponse = [
      {
        versions: [
          {
            id: 'd1',
            version: 1,
            key: 'd',
            engine: 'e1'
          },
          {
            id: 'd2',
            version: 2,
            key: 'd',
            engine: 'e1'
          }
        ]
      },
      {
        versions: [
          {
            id: 'c&1',
            version: 1,
            key: 'c',
            engine: 'e2'
          }
        ]
      }
    ];

    get.withArgs('/api/process-definition/groupedByKey').returns(Promise.resolve({
      json: sinon.stub().returns(
        Promise.resolve(processDefinitionResponse)
      )
    }));

    xmlResponse = {
      d2: 'd2-xml',
      'c&1': 'c1-xml'
    };

    get.withArgs('/api/process-definition/xml?ids=d2&ids=c%261').returns(Promise.resolve({
      json: sinon.stub().returns(
        Promise.resolve(xmlResponse)
      )
    }));

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
      expect(get.calledTwice).to.eql(true);
    });

    it('should dispatch an action with the returned response', () => {
      const expectedResponse = {
        engineCount: 2,
        list: [
          {
            current: {
              ...processDefinitionResponse[0].versions[0],
              bpmn20Xml: xmlResponse.d2
            },
            versions: [
              {
                ...processDefinitionResponse[0].versions[0],
                bpmn20Xml: xmlResponse.d2
              },
              {
                ...processDefinitionResponse[0].versions[1],
              }
            ]
          },
          {
            current: {
              ...processDefinitionResponse[1].versions[0],
              bpmn20Xml: xmlResponse['c&1']
            },
            versions: [
              {
                ...processDefinitionResponse[1].versions[0],
                bpmn20Xml: xmlResponse['c&1']
              }
            ]
          }
        ]
      };

      expect(dispatchAction.calledWith(STOP_ACTION))
        .to.eql(true, 'expected loading to stop');
      expect(
        createLoadProcessDefinitionsResultAction.calledWith(expectedResponse)
      ).to.eql(true, 'expected action to be created with right params');
    });
  });

  describe('request failure', () => {
    const ERROR_MSG ='I_AM_ERROR';
    const ERROR_ACTION = 'ERROR_ACTION';

    let addNotification;
    let createLoadProcessDefinitionsErrorAction;

    beforeEach(() => {
      get = sinon.stub().returns(Promise.reject(ERROR_MSG));
      __set__('get', get);

      addNotification = sinon.spy();
      __set__('addNotification', addNotification);

      createLoadProcessDefinitionsErrorAction = sinon.stub().returns(ERROR_ACTION);
      __set__('createLoadProcessDefinitionsErrorAction', createLoadProcessDefinitionsErrorAction);

      loadProcessDefinitions();
      Promise.runAll();
    });

    afterEach(() => {
      __ResetDependency__('addNotification');
      __ResetDependency__('createLoadProcessDefinitionsErrorAction');
    });

    it('should show an error notification', () => {
      expect(addNotification.calledOnce).to.eql(true);
    });

    it('should create and dispatch a loading error action', () => {
      expect(createLoadProcessDefinitionsErrorAction.calledOnce).to.eql(true);
      expect(createLoadProcessDefinitionsErrorAction.calledWith(ERROR_MSG)).to.eql(true);
      expect(dispatchAction.calledWith(ERROR_ACTION)).to.eql(true);
    });
  });

  describe('openDefinition', () => {
    it('should go to the process display route', () => {
      openDefinition('a');

      expect(router.goTo.calledWith('processDisplay', {definition: 'a'})).to.eql(true);
    });
  });

  describe('setVersionForProcess', () => {
    beforeEach(() => {
      get = sinon.stub().returns(Promise.resolve({
        json: sinon.stub().returns(
          Promise.resolve(processDefinitionResponse)
        )
      }));
      __set__('get', get);
    });

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
