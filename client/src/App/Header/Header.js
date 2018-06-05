import React from 'react';

import * as Styled from './styled.js';

export default function Header() {
  return (
    <Styled.Header>
      <Styled.DashboardLink>Dashboard</Styled.DashboardLink>
      <Styled.ListLink>Instances</Styled.ListLink>
      <Styled.ListLink>Filter</Styled.ListLink>
      <Styled.ListLink>Selections</Styled.ListLink>
      <Styled.ListLink>Incidents</Styled.ListLink>
      <Styled.ProfileDropdown>User Name</Styled.ProfileDropdown>
    </Styled.Header>
  );
}
