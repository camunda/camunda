import React from 'react';
import {Link} from 'react-router-dom';
import {destroy} from 'credentials';
import {get} from 'request';

import './LogoutButton.css';

export default function LogoutButton() {
  return (
    <div className="LogoutButton">
      <Link to="/login" onClick={logout} title="Log out">
        Logout
      </Link>
    </div>
  );
}

async function logout() {
  try {
    await get('/api/authentication/logout');
    destroy();
  } catch (error) {
    alert('Failed to logout due to internal server error');
  }
}
