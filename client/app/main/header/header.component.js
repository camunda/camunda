import {jsx} from 'view-utils';
import {StaticLink} from 'router';
import {AppMenu} from './appMenu';

const template = <header className="cam-brand-header">
  <div className="container-fluid">
    <a className="navbar-brand" title="Camunda Corporate Styles">
      <StaticLink name="default" params={{}}></StaticLink>
      <span className="brand-logo"></span>
      &nbsp;
      <span className="brand-name">Camunda Optimize</span>
    </a>
    <AppMenu/>
  </div>
</header>;

export function Header() {
  return template;
}
