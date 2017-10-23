import React from 'react';
import './Header.css';

export default class Header extends React.Component {
  render() {
    const {name, children} = this.props;
    return (
      <header role='banner' className='Header'>
        <a href='/' title={name}>
          <span className='brand-logo' />
          &nbsp;
          <span>{name}</span>
        </a>
        <nav>
          <ul>
            {children}
          </ul>
        </nav>
      </header>
    );
  }
}
