import {onNextTick} from './onNextTick';

export function createQueue() {
  const tasks = [];
  let isRunning = false;

  return {
    addTask,
    isWaiting
  };

  function addTask(task) {
    tasks.push(task);

    if (!isRunning) {
      runQueue();
    }

    return isWaiting.bind(null, task);
  }

  function isWaiting(task) {
    return tasks.indexOf(task) >= 0;
  }

  // It basically executes first task asynchroniously and then checks if there
  // any tasks left if so it starts another run of runQueue function until
  // all tasks are executed.
  function runQueue() {
    isRunning = true;

    // it probably could be expressed much easier with while loop by using
    // async await syntax in future
    onNextTick(() => {
      executeNextTask(() => {
        if (tasks.length === 0) {
          isRunning = false;
        } else {
          runQueue();
        }
      });
    });
  }

  // Executes task function that can be either asynchronious or synchriouns.
  // After task is executed callback is called to let loop know that it can
  // continue execution.
  function executeNextTask(callback) {
    const task = tasks[0];

    if (task.length === 1) {
      return task(done);
    }

    task();
    done();

    function done() {
      tasks.shift();

      callback();
    }
  }
}
