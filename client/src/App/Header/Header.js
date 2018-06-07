import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

import {Badge} from 'components';

export default function Header({active, detail, ...props}) {
  function createBadgeEntry(label) {
    const type = label.toLowerCase();
    return (
      <Styled.ListLink active={active === 'instances'}>
        <span>{label}</span>
        <Badge type={type}>{props[type]}</Badge>
      </Styled.ListLink>
    );
  }

  return (
    <Styled.Header>
      <Styled.DashboardLink active={active === 'dashboard'}>
        Dashboard
      </Styled.DashboardLink>
      {createBadgeEntry('Instances')}
      {props.filters > 0 && createBadgeEntry('Filters')}
      {props.selections > 0 && createBadgeEntry('Selections')}
      {props.incidents > 0 && createBadgeEntry('Incidents')}
      {detail && <Styled.Detail>Instance {detail}</Styled.Detail>}
      <Styled.ProfileDropdown>User Name</Styled.ProfileDropdown>
    </Styled.Header>
  );
}

Header.propTypes = {
  active: PropTypes.oneOf(['dashboard', 'instances', 'detail']).isRequired,
  instances: PropTypes.number,
  filters: PropTypes.number,
  selections: PropTypes.number,
  incidents: PropTypes.number,
  detail: PropTypes.string
};
