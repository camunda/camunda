import {jsx, Match} from 'view-utils';
import {Header} from './header';
import {Footer} from './footer';
import {Router, RouteView, StaticLink} from 'router';
import {LoginRoot, Authenticated} from 'login';
import {LoginForm} from './loginForm';
import {DynamicLoader} from 'dynamicLoader';

export function Main() {
  return <Router routerProperty="router">
    <LoginRoot>
      <div className="container">
        <Header/>
        <div className="content">
          <div class="content__views">
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
        <Footer/>
      </div>
    </LoginRoot>
  </Router>;
}
