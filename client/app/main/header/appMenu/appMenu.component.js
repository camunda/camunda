import {jsx, OnEvent, Match, Case} from 'view-utils';
import {getLogin, clearLogin} from 'login';

export function AppMenu() {
  return <nav className="app-menu">
    <ul>
      <Match>
        <Case predicate={getLogin}>
          <li>
            <a href="#/login">
              Logout
              <OnEvent event={['click']} listener={clearLogin} />
            </a>
          </li>
        </Case>
      </Match>
    </ul>
  </nav>;
}
