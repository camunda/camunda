import 'main/routing.config';
import {getRouter} from 'router';

export function initRouter() {
  const router = getRouter();

  router.onUrlChange();
}
