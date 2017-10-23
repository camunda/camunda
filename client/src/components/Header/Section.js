import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import './Section.css';

export default withRouter(function Section({name, linksTo, active, location}) {
  const isActive = location.pathname.includes(active);

  return (
    <li className={`Section${isActive ? ' active' : ''}`}>
      <Link to={linksTo}>
        {name}
      </Link>
    </li>
  );
})
