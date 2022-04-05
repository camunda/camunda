/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';
import {
  mockGetEmptyTasks,
  mockGetAllOpenTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
} from 'modules/queries/get-tasks';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskUnclaimedWithForm,
  mockGetTaskClaimedWithForm,
} from 'modules/queries/get-task';
import {mockGetForm} from 'modules/queries/get-form';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
} from 'modules/queries/get-task-variables';
import {
  mockGetSelectedVariables,
  mockGetSelectedVariablesEmptyVariables,
} from 'modules/queries/get-selected-variables';
import {Layout} from './Layout';
import {EmptyDetails} from 'EmptyDetails';
import {Task} from './Task';

export default {
  title: 'Pages States/Tasklist',
};

interface Props {
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'];
  mocks: React.ComponentProps<typeof MockedApolloProvider>['mocks'];
}

const Wrapper: React.FC<Props> = ({initialEntries, mocks}) => {
  return (
    <MockedApolloProvider mocks={mocks}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path={Pages.Initial()} element={<Layout />}>
            <Route
              index
              element={
                <EmptyDetails>Select a Task to view the details</EmptyDetails>
              }
            />
            <Route path={Pages.TaskDetails()} element={<Task />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </MockedApolloProvider>
  );
};

const EmptyPage: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetEmptyTasks]}
      initialEntries={['/']}
    />
  );
};

const AllOpenTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetAllOpenTasks()]}
      initialEntries={['/']}
    />
  );
};

const ClaimedByMeTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetClaimedByMe]}
      initialEntries={['/?filter=claimed-by-me']}
    />
  );
};

const UnclaimedTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetUnclaimed]}
      initialEntries={['/?filter=unclaimed']}
    />
  );
};

const CompletedTasks: React.FC = () => {
  return (
    <Wrapper
      mocks={[mockGetCurrentUser, mockGetCompleted]}
      initialEntries={['/?filter=completed']}
    />
  );
};

const Unclaimed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimed(),
        mockGetTaskEmptyVariables(),
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    />
  );
};

const Claimed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskUnclaimed(),
        mockGetTaskEmptyVariables(),
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    />
  );
};

const Completed: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetCompleted,
        mockGetTaskCompleted(),
        mockGetTaskEmptyVariables(),
        mockGetCompleted,
      ]}
      initialEntries={['/0?filter=completed']}
    />
  );
};

const UnclaimedWithVariables: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimed(),
        mockGetTaskVariables(),
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    />
  );
};

const ClaimedWithVariables: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskUnclaimed(),
        mockGetTaskVariables(),
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    />
  );
};

const UnclaimedWithForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariablesEmptyVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    />
  );
};

const UnclaimedWithPrefilledForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    />
  );
};

const UnclaimedWithNoEditPermission: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentRestrictedUser,
        mockGetUnclaimed,
        mockGetTaskUnclaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]}
      initialEntries={['/0?filter=unclaimed']}
    />
  );
};

const ClaimedWithForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariablesEmptyVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    />
  );
};

const ClaimedWithPrefilledForm: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    />
  );
};

const ClaimedWithNoEditPermission: React.FC = () => {
  return (
    <Wrapper
      mocks={[
        mockGetCurrentRestrictedUser,
        mockGetAllOpenTasks(),
        mockGetTaskClaimedWithForm(),
        mockGetSelectedVariables(),
        mockGetForm,
        mockGetAllOpenTasks(),
      ]}
      initialEntries={['/0']}
    />
  );
};

export {
  EmptyPage,
  AllOpenTasks,
  ClaimedByMeTasks,
  UnclaimedTasks,
  CompletedTasks,
  Unclaimed,
  Claimed,
  Completed,
  UnclaimedWithVariables,
  ClaimedWithVariables,
  UnclaimedWithForm,
  UnclaimedWithPrefilledForm,
  UnclaimedWithNoEditPermission,
  ClaimedWithForm,
  ClaimedWithPrefilledForm,
  ClaimedWithNoEditPermission,
};
