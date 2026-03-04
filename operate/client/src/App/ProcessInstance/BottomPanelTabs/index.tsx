/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet} from 'react-router-dom';
import {Container} from './styled';
import {TabListNav} from './TabListNav';

const BottomPanelTabs: React.FC = () => {
  return (
    <Container>
      <TabListNav
        label="foo"
        items={[
          {key: 'bar', title: 'Bar', label: 'Bar', selected: true, to: {}},
        ]}
      />
      <Outlet />
    </Container>
  );
};

export {BottomPanelTabs};
