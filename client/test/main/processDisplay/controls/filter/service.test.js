import {expect} from 'chai';
import sinon from 'sinon';
import {deleteFilter, getFilter,
        __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/service';

describe('Filter service', () => {
  const DELETE_FILTER = 'DELETE_FILTER';

  let dispatch;
  let createDeleteFilterAction;
  let parse;
  let getLastRoute;

  beforeEach(() => {
    dispatch = sinon.spy();
    __set__('dispatch', dispatch);

    createDeleteFilterAction = sinon.stub().returns(DELETE_FILTER);
    __set__('createDeleteFilterAction', createDeleteFilterAction);

    parse = sinon.stub().returns('parsed');
    __set__('parse', parse);

    getLastRoute = sinon.stub().returns({
      params: 'params'
    });
    __set__('getLastRoute', getLastRoute);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('createDeleteFilterAction');
    __ResetDependency__('parse');
    __ResetDependency__('getLastRoute');
  });

  describe('deleteFilter', () => {
    const filter = {start: 'date', end: 'anotherDate'};

    beforeEach(() => {
      deleteFilter(filter);
    });

    it('should dispatch delete filter action', () => {
      expect(dispatch.calledWith(DELETE_FILTER)).to.eql(true);
    });

    it('should create action', () => {
      expect(createDeleteFilterAction.calledOnce).to.eql(true);
      expect(createDeleteFilterAction.calledWith(filter)).to.eql(true);
    });
  });

  describe('getFilter', () => {
    it('should return parsed last route params', () => {
      expect(getFilter()).to.eql('parsed');
      expect(getLastRoute.calledOnce)
        .to.eql(true, 'expected last route to be fetched');
      expect(parse.calledWith('params'))
        .to.eql(true, 'expected params to be parsed');
    });
  });
});
