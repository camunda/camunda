import {jsx, Match} from 'view-utils';
import {Header} from './header';
import {Footer} from './footer';
import {Router, RouteView} from 'router';
import {LoginRoot, Authenticated} from 'login';
import {LoginForm} from './loginForm';
import {DynamicLoader} from 'dynamicLoader';
import {Notifications} from './notifications';

export function Main() {
  return <Router selector="router">
    <LoginRoot>
      <Notifications selector="notifications" />
      <Header />
      <div className="site-wrap">
        <div className="page-wrap">
          <Match>
            <RouteView name="login">
              <LoginForm selector="loginForm" />
            </RouteView>
            <RouteView name="default">
              <Authenticated routeName="login">
                <DynamicLoader module="processDisplay" selector="processDisplay" />
              </Authenticated>
            </RouteView>
          </Match>
        </div>
      </div>
      <Footer />
    </LoginRoot>
  </Router>;
}
