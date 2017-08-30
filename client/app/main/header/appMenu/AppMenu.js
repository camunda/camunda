import React from 'react';
import {getLogin, clearLogin} from 'login';

const jsx = React.createElement;

export function AppMenu() {
  const logoutBtn  = <li>
    <a href="#/login" onClick={clearLogin}>
      <span className="glyphicon glyphicon-off"></span>&nbsp;
      Logout
    </a>
  </li>;

  return <nav className="app-menu">
    <ul>
      {getLogin() ? logoutBtn : ''}
    </ul>
  </nav>;
}
