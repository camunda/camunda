import {expect} from 'chai';
import sinon from 'sinon';
import {createChangeObserver} from 'utils';

describe('createChangeObserver', () => {
  let observer;

  beforeEach(() => {
    observer = createChangeObserver({
      getter: ({a}) => a
    });
  });

  it('should create observer with observeChanges and setLast functions', () => {
    expect(typeof observer.observeChanges).to.eql('function');
    expect(typeof observer.setLast).to.eql('function');
  });

  describe('observeChanges', () => {
    let listener;
    let trigger;

    beforeEach(() => {
      listener = sinon.spy();

      trigger = observer.observeChanges(listener);
    });

    it('should execute listener after first call', () => {
      trigger({a: 2});

      expect(listener.calledWith(2)).to.eql(true);
    });

    it('should execute listener when value returned by getter changes', () => {
      trigger({a: 2});
      trigger({a: 3});

      expect(listener.calledTwice).to.eql(true);
      expect(listener.calledWith(2)).to.eql(true);
      expect(listener.calledWith(3)).to.eql(true);
    });

    it('should not execute listener when value returned by getter stays the same', () => {
      trigger({a: 2});
      trigger({a: 2, b: 2});

      expect(listener.calledOnce).to.eql(true);
    });

    it('should execute listener when last value was expiclitly changed by setLast', () => {
      trigger({a: 2});
      observer.setLast(3);
      trigger({a: 2, b: 2});

      expect(listener.calledTwice).to.eql(true);
    });
  });
});
