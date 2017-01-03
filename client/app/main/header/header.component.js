import {jsx} from 'view-utils';

const template = <header className="cam-brand-header">
  <div className="container-fluid">
    <a className="navbar-brand" href="/index.html" title="Camunda Corporate Styles">
      <span className="brand-logo"></span>
      <span className="brand-name">Camunda Optimize</span>
    </a>
  </div>
</header>;

export function Header() {
  return template;
}
