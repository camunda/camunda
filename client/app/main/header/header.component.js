import {jsx} from 'view-utils';
import {StaticLink} from 'router';
import {AppMenu} from './appMenu';

const template = <header>
  <div className="navbar-header">
    <a className="navbar-brand" title="Camunda Optimize">
      <StaticLink name="default" params={{}}></StaticLink>
      <span className="brand-logo"></span>
      &nbsp;
      <span className="brand-name">Camunda Optimize</span>
    </a>
  </div>
  <AppMenu/>
</header>;

export function Header() {
  return template;
}
