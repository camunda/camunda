import {jsx, OnEvent} from 'view-utils';
import {getRouter} from 'router';
import {AppMenu} from './appMenu';

const router = getRouter();

export function Header({redirect}) {
  return  <header>
    <div className="navbar-header">
      <a href="/" className="navbar-brand" title="Camunda Optimize">
        <OnEvent event="click" listener={goToRoot} />
        <span className="brand-logo"></span>
        &nbsp;
        <span className="brand-name">Camunda Optimize</span>
      </a>
    </div>
    <AppMenu/>
  </header>;

  function goToRoot({event}) {
    if (!redirect) {
      event.preventDefault();
    }

    router.goTo('default', {});
  }
}
