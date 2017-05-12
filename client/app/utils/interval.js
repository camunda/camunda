import {$window} from 'view-utils';

export function interval(task, delay = 1000) {
  const id = $window.setInterval(task, delay);

  return $window.clearInterval.bind($window, id);
}
