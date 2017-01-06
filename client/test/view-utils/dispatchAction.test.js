import {expect} from 'chai';
import sinon from 'sinon';
import {dispatchAction, ACTION_EVENT_NAME, __set__, __ResetDependency__} from 'view-utils/dispatchAction';

describe('dispatchAction', () => {
  const action = 'action-1';
  let actionEvent;
  let $document;

  beforeEach(() => {
    actionEvent = {
      initEvent: sinon.spy()
    };

    $document = {
      createEvent: sinon.stub().returns(actionEvent),
      dispatchEvent: sinon.spy()
    };
    __set__('$document', $document);

    dispatchAction(action);
  });

  afterEach(() => {
    __ResetDependency__('$document');
  });

  it('should create custom event', () => {
    expect($document.createEvent.calledWith('CustomEvent')).to.eql(true);
  });

  it('should init custom event', () => {
    expect(actionEvent.initEvent.calledWith(ACTION_EVENT_NAME)).to.eql(true);
  });

  it('should set reduxAction on event', () => {
    expect(actionEvent.reduxAction).to.equal(action);
  });

  it('should dispatch custom event', () => {
    expect($document.dispatchEvent.calledWith(actionEvent)).to.eql(true);
  });
});
