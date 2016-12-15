import {jsx} from 'view-utils';
import {Header} from './header';
import {Footer} from './footer';
import {Router, RouteView, StaticLink} from 'router';

export function Main() {
  return <Router>
    <div className="container">
      <Header/>
      <div className="content">
        <a>
          <StaticLink name="a" params={{a: 'alina', b: 24}}></StaticLink>
          open a
        </a>
        <RouteView name="a">
          aaa
          <a>
            <StaticLink name="b" params={{b: 'john', c: 50}}></StaticLink>
            open b
          </a>
        </RouteView>
        <RouteView name="b">bbb</RouteView>
      </div>
      <Footer/>
    </div>
  </Router>;
}
