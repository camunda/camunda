import {expect} from 'chai';
import sinon from 'sinon';
import {interval, __set__, __ResetDependency__} from 'utils/interval';

describe('interval', () => {
  const id = 12345;
  let $window;

  beforeEach(() => {
    $window = {
      setInterval: sinon.stub().returns(id),
      clearInterval: sinon.spy()
    };

    __set__('$window', $window);
  });

  afterEach(() => {
    __ResetDependency__('$window');
  });

  it('should have start interval with given task and delay', () => {
    const task = 'task';
    const delay = 10203;

    interval(task, delay);

    expect($window.setInterval.calledWith(task, delay)).to.eql(true);
  });

  it('should have start interval with given task and default delay', () => {
    const task = 'task';

    interval(task);

    const [, delay] = $window.setInterval.lastCall.args;

    expect($window.setInterval.calledWith(task)).to.eql(true);
    expect(typeof delay).to.eql('number');
  });

  it('should return function that removes interval', () => {
    const cancel = interval('task');

    cancel();

    expect($window.clearInterval.calledWith(id)).to.eql(true);
  });
});
