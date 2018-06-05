import React from 'react';

import * as Styled from './styled.js';

import {Badge} from 'components';

export default function Header() {
  return (
    <Styled.Header>
      <Styled.DashboardLink>Dashboard</Styled.DashboardLink>
      <Styled.ListLink>
        Instances <Badge>14576</Badge>
      </Styled.ListLink>
      <Styled.ListLink>
        Filter <Badge type="filters">9263</Badge>
      </Styled.ListLink>
      <Styled.ListLink>
        Selections <Badge type="selections">24</Badge>
      </Styled.ListLink>
      <Styled.ListLink>
        Incidents <Badge type="incidents">328</Badge>
      </Styled.ListLink>
      <Styled.ProfileDropdown>User Name</Styled.ProfileDropdown>
    </Styled.Header>
  );
}
