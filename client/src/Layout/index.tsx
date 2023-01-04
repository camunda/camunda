/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {Outlet} from 'react-router-dom';
import {Header} from './Header';
import {Filters} from './Filters';
import {Tasks} from './Tasks';
import {Container, TasksPanel, DetailsPanel} from './styled';

const Layout: React.FC = () => {
  return (
    <>
      <Header />
      <Container>
        <TasksPanel title="Tasks">
          <Filters />
          <Tasks />
        </TasksPanel>
        <DetailsPanel title="Details" variant="layer">
          <Outlet />
        </DetailsPanel>
      </Container>
    </>
  );
};

export {Layout};
