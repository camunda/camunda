import React from 'react';
import {getLogin, clearLogin} from 'login';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export function AppMenuReact() {
  return <nav className="app-menu">
    <ul>
      {getLogoutBtn()}
    </ul>
  </nav>;
}

function getLogoutBtn() {
  if (getLogin()) {
    return <li>
      <a href="#/login" onClick={clearLogin}>
        <span className="glyphicon glyphicon-off"></span>&nbsp;
        Logout
      </a>
    </li>;
  }
}

export const AppMenu = createViewUtilsComponentFromReact('div', AppMenuReact);
