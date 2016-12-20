import './polyfill';
import {Main, reducer} from 'main';
import {mountMain} from './mount';
import {initStore} from './store';
import {initRouter} from './router';
import {refreshAuthentication} from 'main/login';

const updateComponent = mountMain(Main);

initStore(updateComponent, reducer);
initRouter();
refreshAuthentication();
