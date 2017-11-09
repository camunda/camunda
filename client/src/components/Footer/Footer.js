import React from 'react';
import './Footer.css';

export default function Footer({version}) {
  return (
    <footer className='Footer'>
      © Camunda Services GmbH 2017, All Rights Reserved. | {version && `${version}`}
    </footer>
  );
}
