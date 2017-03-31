import {expect} from 'chai';
import sinon from 'sinon';
import {createQueue, __set__, __ResetDependency__} from 'utils/createQueue';

describe('createQueue', () => {
  let onNextTick;
  let queue;

  beforeEach(() => {
    onNextTick = sinon.stub();
    __set__('onNextTick', onNextTick);

    queue = createQueue();
  });

  afterEach(() => {
    __ResetDependency__('onNextTick');
  });

  describe('addTask', () => {
    it('should excute tasks in given order', () => {
      let t = 0;

      onNextTick.callsArg(0);

      queue.addTask(() => t += 3);
      queue.addTask(() => t *= 2);

      expect(t).to.eql(6);
    });

    it('should return function that tells if task has not been run yet', () => {
      const isWaiting = queue.addTask(sinon.spy());

      expect(isWaiting()).to.eql(true);

      onNextTick.firstCall.args[0]();

      expect(isWaiting()).to.eql(false);
    });
  });

  describe('isWaiting', () => {
    it('should return true if task is waiting', () => {
      const task = sinon.spy();

      queue.addTask(task);

      expect(queue.isWaiting(task)).to.eql(true);

      onNextTick.firstCall.args[0]();

      expect(queue.isWaiting(task)).to.eql(false);
    });

    it('should return false only for finnished task', () => {
      const task1 = sinon.spy();
      const task2 = sinon.spy();

      queue.addTask(task1);
      queue.addTask(task2);

      expect(queue.isWaiting(task1)).to.eql(true);
      expect(queue.isWaiting(task2)).to.eql(true);

      onNextTick.firstCall.args[0]();

      expect(queue.isWaiting(task1)).to.eql(false);
      expect(queue.isWaiting(task2)).to.eql(true);
    });
  });
});
