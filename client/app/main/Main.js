import {jsx, Match} from 'view-utils';
import {Header} from './header';
import {Footer} from './footer';
import {Router, RouteView} from 'router';
import {LoginRoot, Authenticated} from 'login';
import {LoginForm} from './loginForm';
import {DynamicLoader} from 'dynamicLoader';
import {Notifications} from 'notifications';
import {checkLicenseAndNotifyIfExpiresSoon} from 'license/service';
import {runOnce, onNextTick} from 'utils';

export function Main() {
  const template = <Router selector="router">
    <LoginRoot>
      <Header />
      <div className="site-wrap">
        <div className="page-wrap">
          <Match>
            <RouteView name="login">
              <LoginForm selector="loginForm" />
            </RouteView>
            <RouteView name="default">
              <Authenticated routeName="login">
                <DynamicLoader module="processSelection" selector="processSelection" />
              </Authenticated>
            </RouteView>
            <RouteView name="processDisplay">
              <Authenticated routeName="login">
                <DynamicLoader module="processDisplay" selector="processDisplay" />
              </Authenticated>
            </RouteView>
          </Match>
        </div>
      </div>
      <Notifications selector="notifications" />
      <Footer selector="footer" />
    </LoginRoot>
  </Router>;

  return (node, eventsBus) => {
    return [
      template(node, eventsBus),
      runOnce(() => {
        onNextTick(checkLicenseAndNotifyIfExpiresSoon);
      })
    ];
  };
}
