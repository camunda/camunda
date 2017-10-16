import React from 'react';
import './Footer.css';

export default class Footer extends React.Component {
  render() {
    const {version} = this.props;
    return (
      <footer className='Footer'>
        © Camunda services GmbH 2017, All Rights Reserved {version && `/ ${version}`}
      </footer>
    );
  }
}