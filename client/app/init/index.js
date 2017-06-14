import {mountMain} from './mount';
import {initStore} from './store';
import {initRouter} from './router';
import {refreshAuthentication} from 'login';

export function init(Main, reducer) {
  const updateComponent = mountMain(Main);

  initStore(updateComponent, reducer);
  initRouter();
  refreshAuthentication();
}
