import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {resetStatisticData, loadStatisticData, findSequenceFlowBetweenGatewayAndActivity,
        __set__, __ResetDependency__} from 'main/processDisplay/statistics/service';

describe('Statistics service', () => {
  setupPromiseMocking();

  let statisticsData;
  let filterQuery;

  let dispatchAction;
  let getFilterQuery;
  let addNotification;
  let getDefinitionId;

  let createLoadCorrelationAction;
  let createLoadCorrelationResultAction;
  let createResetCorrelationAction;
  let getFilter;

  const LOAD_ACTION = 'LOAD_CORRELATION';
  const RESULT_ACTION = 'CORRELATION_RESULT';
  const RESET_ACTION = 'RESET_CORRELATION';
  const definitionId = 'some_definition_id';

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    statisticsData = {
      endEvent : 'EndEvent_0ip3gsn',
      total : 1,
      followingNodes : {
        Task_1i4dc60 : {
          activitiesReached : 0,
          activityCount : 3,
          activityId : 'Task_1i4dc60'
        },
        Task_1ba8t9m : {
          activitiesReached : 1,
          activityCount : 7,
          activityId : 'Task_1ba8t9m'
        }
      }
    };

    filterQuery = {
      dates: []
    };

    getFilterQuery = sinon.stub().returns(filterQuery);
    __set__('getFilterQuery', getFilterQuery);

    getDefinitionId = sinon.stub().returns(definitionId);
    __set__('getDefinitionId', getDefinitionId);

    addNotification = sinon.spy();
    __set__('addNotification', addNotification);

    createLoadCorrelationAction = sinon.stub().returns(LOAD_ACTION);
    __set__('createLoadCorrelationAction', createLoadCorrelationAction);

    createLoadCorrelationResultAction = sinon.stub().returns(RESULT_ACTION);
    __set__('createLoadCorrelationResultAction', createLoadCorrelationResultAction);

    createResetCorrelationAction = sinon.stub().returns(RESET_ACTION);
    __set__('createResetCorrelationAction', createResetCorrelationAction);

    getFilter = sinon.stub().returns([]);
    __set__('getFilter', getFilter);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('getDefinitionId');
    __ResetDependency__('getFilterQuery');
    __ResetDependency__('addNotification');
    __ResetDependency__('createLoadCorrelationAction');
    __ResetDependency__('createLoadCorrelationResultAction');
    __ResetDependency__('createResetCorrelationAction');
    __ResetDependency__('getFilter');
  });

  describe('reset statistic data', () => {
    it('should dispatch an action', () => {
      resetStatisticData();

      expect(dispatchAction.calledWith(RESET_ACTION)).to.eql(true);
    });
  });

  describe('load statistic data', () => {
    let post;
    let selection;

    beforeEach(() => {
      selection = {
        endEvent: 'EndEvent_0ip3gsn',
        gateway: 'Gateway_2jc2dh3'
      };
    });

    describe('success', () => {
      beforeEach(() => {
        post = sinon.stub().returns(Promise.resolve({
          json: sinon.stub().returns(
            Promise.resolve(statisticsData)
          )
        }));
        __set__('post', post);

        loadStatisticData(selection);
        Promise.runAll();
      });

      afterEach(() => {
        __ResetDependency__('post');
      });

      it('should dispatch an action when it starts loading data', () => {
        expect(dispatchAction.calledWith(LOAD_ACTION)).to.eql(true);
      });

      it('should load the data with the required payload', () => {
        expect(post.calledOnce).to.eql(true);

        const {end, gateway, processDefinitionId, filter} = post.args[0][1];

        expect(end).to.eql('EndEvent_0ip3gsn');
        expect(gateway).to.eql('Gateway_2jc2dh3');
        expect(processDefinitionId).to.eql(definitionId);
        expect(filter.dates).to.exist;
        expect(filter.dates).to.be.empty;
      });

      it('should dispatch an action with the returned response', () => {
        expect(dispatchAction.calledWith(RESULT_ACTION));
        expect(createLoadCorrelationResultAction.calledWith(statisticsData));
      });
    });

    describe('failure', () => {
      beforeEach(() => {
        post = sinon.stub().returns(Promise.reject('I AM ERROR'));
        __set__('post', post);

        loadStatisticData(selection);
        Promise.runAll();
      });

      afterEach(() => {
        __ResetDependency__('post');
      });

      it('should show an error message when the request fails', () => {
        expect(addNotification.calledOnce).to.eql(true);

        const {isError} = addNotification.args[0][0];

        expect(isError).to.eql(true);
      });
    });
  });

  describe('find sequence flow between gateway and activity', () => {
    let elementRegistry;
    const sequenceFlow = 'SEQUENCE_FLOW';
    const gateway = 'Gateway_2jc2dh3';
    const activity = 'Task_1i4dc60';

    beforeEach(() => {
      elementRegistry = {
        get: sinon.stub().returns({
          outgoing: [sequenceFlow],
          incoming: [sequenceFlow]
        })
      };
    });

    it('should return a sequence flow', () => {
      const flow = findSequenceFlowBetweenGatewayAndActivity(elementRegistry, gateway, activity);

      expect(flow).to.eql(sequenceFlow);
    });
  });
});
