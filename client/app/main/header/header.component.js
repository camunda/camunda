import {jsx} from 'view-utils';
import {StaticLink} from 'router';

const template = <header className="cam-brand-header">
  <div className="container-fluid">
    <a className="navbar-brand" title="Camunda Corporate Styles">
      <StaticLink name="default" params={{}}></StaticLink>
      <span className="brand-logo"></span>
      <span className="brand-name">Camunda Optimize</span>
    </a>
  </div>
</header>;

export function Header() {
  return template;
}
