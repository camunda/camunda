import React from 'react';
import {Link} from 'react-router-dom';
import {destroy} from 'credentials';
import {get} from 'request';

import './LogoutButton.css';

export default function LogoutButton() {
  return (
    <li className='LogoutButton'>
      <Link to='/login' onClick={logout}>
        Logout
      </Link>
    </li>
  );
}

function logout() {
  get('/api/authentication/logout');
  destroy();
}