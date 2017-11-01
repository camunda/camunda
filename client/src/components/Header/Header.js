import React from 'react';
import {Link} from 'react-router-dom';

import LogoutButton from './LogoutButton';
import Section from './Section';

import {getToken} from 'credentials';

import './Header.css';

export default function Header({name}) {
  return (
    <header role='banner' className='Header'>
      <Link to='/' className='Header__link' title={name}>
        <span className='Header__brand-logo' />
        <span>{name}</span>
      </Link>
      {(getToken() &&
        <ul role='navigation' className='Header__nav'>
          <Section name='Dashboards' linksTo='/dashboards' active='/dashboard' />
          <Section name='Reports' linksTo='/reports' active='/report' />
        </ul>
      )}
      {(getToken() &&
        <LogoutButton />
      )}
    </header>
  );
}
