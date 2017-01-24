import {mountMain} from './mount';
import {initStore} from './store';
import {initRouter} from './router';
import {refreshAuthentication} from 'login';
import {Main, reducer} from 'main';

const updateComponent = mountMain(Main);

initStore(updateComponent, reducer);
initRouter();
refreshAuthentication();
