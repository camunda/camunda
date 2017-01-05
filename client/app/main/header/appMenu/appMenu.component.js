import {jsx, OnEvent, Match, Case} from 'view-utils';
import {getLogin, clearLogin} from 'login';

export function AppMenu() {
  return <nav className="app-menu">
    <ul>
      <Match>
        <Case predicate={getLogin}>
          <li>
            <button className="btn btn-link" style="line-height: 46px;">
              Logout
              <OnEvent event={['click']} listener={clearLogin} />
            </button>
          </li>
        </Case>
      </Match>
    </ul>
  </nav>;
}
