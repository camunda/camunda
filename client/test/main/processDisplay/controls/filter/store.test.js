import {expect} from 'chai';
import sinon from 'sinon';
import jsurl from 'jsurl';
import {
  dispatch, parse, format, onHistoryStateChange,
  __set__, __ResetDependency__
} from 'main/processDisplay/controls/filter/store';

describe('Filter store', () => {
  const dispatchState = 'dis-state';
  const trigger = 'trigger';
  let router;
  let dispatchToReducer;
  let observer;

  beforeEach(() => {
    router = {
      addHistoryListener: sinon.stub().returnsThis()
    };
    __set__('router', router);

    dispatchToReducer = sinon.stub().returns(dispatchState);
    __set__('dispatchToReducer', dispatchToReducer);

    observer = {
      setLast: sinon.spy(),
      observeChanges: sinon.stub().returns(trigger)
    };
    __set__('observer', observer);
  });

  afterEach(() => {
    __ResetDependency__('router');
    __ResetDependency__('dispatchToReducer');
    __ResetDependency__('observer');
  });

  describe('dispatch', () => {
    let action;

    beforeEach(() => {
      action = {a: 1};
    });

    it('should pass action to reducer dispatch function', () => {
      dispatch(action);

      expect(dispatchToReducer.calledWith(action)).to.eql(true);
    });

    it('should set new state as last value of observer', () => {
      dispatch(action);

      expect(observer.setLast.calledWith(dispatchState)).to.eql(true);
    });
  });

  describe('parse', () => {
    it('should decode filter state using jsurl', () => {
      const filter = ['alina', 'martha'];
      const params = {
        filter: jsurl.stringify(filter)
      };

      expect(parse(params)).to.eql(filter);
    });
  });

  describe('format', () => {
    it('should add formatted filter to params', () => {
      const filter = ['alina', 'martha'];

      expect(format(
        {
          a: 1
        },
        filter
      )).to.eql({
        a: 1,
        filter: jsurl.stringify(filter)
      });
    });
  });

  describe('onHistoryStateChange', () => {
    let listener;

    beforeEach(() => {
      listener = sinon.spy();
    });

    it('should observe changes', () => {
      onHistoryStateChange(listener);

      expect(observer.observeChanges.calledWith(listener)).to.eql(true);
    });

    it('should add history listener', () => {
      const remove = onHistoryStateChange(listener);

      expect(router.addHistoryListener.calledWith(trigger)).to.eql(true);
      expect(remove).to.eql(router);
    });
  });
});
