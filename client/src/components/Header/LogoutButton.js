import React from 'react';
import {getToken, destroy} from 'credentials';
import {get} from 'request';

import './LogoutButton.css';

export default function LogoutButton() {
  return (
    <li className={`LogoutButton${getToken() ? '' : ' hidden'}`}>
      <a href="#/login" onClick={logout}>
        Logout
      </a>
    </li>
  );
}

function logout() {
  get('/api/authentication/logout');
  destroy();
}