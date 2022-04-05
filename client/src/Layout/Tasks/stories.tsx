/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {Task} from './Task';

export default {
  title: 'Components/Tasklist/Left Panel',
};

const UL = styled.ul`
  width: 478px;
`;

const TaskCard: React.FC = () => {
  return (
    <MemoryRouter initialEntries={['/2251799813685885']}>
      <Routes>
        <Route
          path="/:id"
          element={
            <UL>
              <Task
                taskId="2251799813685883"
                name="Register the passenger"
                processName="Flight registration"
                assignee={currentUser.userId}
                creationTime="2020-05-22T13:39:31.139+0000"
              />
              <Task
                taskId="2251799813685884"
                name="Register the passenger"
                processName="Flight registration"
                assignee={null}
                creationTime="2020-03-21T15:39:31.139+0000"
              />
              <Task
                taskId="2251799813685885"
                name="Register the passenger"
                processName="Flight registration"
                assignee={null}
                creationTime="2020-09-22T15:39:31.139+0000"
              />
            </UL>
          }
        />
      </Routes>
    </MemoryRouter>
  );
};

export {TaskCard};
