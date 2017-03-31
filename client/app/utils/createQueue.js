import {$window} from 'view-utils';

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

  function runQueue() {
    isRunning = true;

    $window.setTimeout(() => {
      executeNextTask(() => {
        if (tasks.length === 0) {
          isRunning = false;
        } else {
          runQueue();
        }
      });
    }, 0);
  }

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
