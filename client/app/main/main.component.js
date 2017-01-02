import {jsx, Match, Default} from 'view-utils';
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
              <RouteView name="a">
                aaa
                <a>
                  <StaticLink name="b" params={{b: 'john', c: 50}}></StaticLink>
                  open b
                </a>
              </RouteView>
              <RouteView name="b">
                <Authenticated routeName="login">
                  <DynamicLoader module="b" selector="b" />
                </Authenticated>
              </RouteView>
              <RouteView name="login">
                <LoginForm selector="loginForm" />
              </RouteView>
              <Default>
                <a>
                  <StaticLink name="a" params={{a: 'alina', b: 24}}></StaticLink>
                  open a
                </a>
                <br />
                <a>
                  <StaticLink name="b" params={{b: 'john', c: 50}}></StaticLink>
                  open b
                </a>
              </Default>
            </Match>
          </div>
        </div>
        <Footer/>
      </div>
    </LoginRoot>
  </Router>;
}
