/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, TasksPanel, DetailsPanel} from './styled';
import {Task} from './Task';
import {Filters} from './Filters';
import {AvailableTasks} from './AvailableTasks';
import {EmptyPage} from './EmptyPage';
import {Route, Routes} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';

const Tasks: React.FC = () => {
  return (
    <Container>
      <TasksPanel title="Tasks">
        <Filters />
        <AvailableTasks />
      </TasksPanel>
      <DetailsPanel title="Details" variant="layer">
        <Routes>
          <Route index element={<EmptyPage />} />
          <Route path={Pages.TaskDetails()} element={<Task />} />
        </Routes>
      </DetailsPanel>
    </Container>
  );
};

export {Tasks};
