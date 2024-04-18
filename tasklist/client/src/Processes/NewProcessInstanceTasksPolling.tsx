/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {observer} from 'mobx-react-lite';
import {pages} from 'modules/routing';
import {newProcessInstance} from 'modules/stores/newProcessInstance';
import {Task} from 'modules/types';
import {useLocation, useNavigate} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {useQuery} from '@tanstack/react-query';
import {request, RequestError} from 'modules/request';
import {api} from 'modules/api';

type NewTasksResponse = Task[];

const NewProcessInstanceTasksPolling: React.FC = observer(() => {
  const {instance} = newProcessInstance;
  const navigate = useNavigate();
  const location = useLocation();

  useQuery<NewTasksResponse, RequestError | Error>({
    queryKey: ['newTasks', instance?.id],
    enabled: instance !== null,
    refetchInterval: 1000,
    queryFn: async () => {
      const id = instance?.id;
      if (id === undefined) {
        throw new Error('Process instance id is undefined');
      }

      const {response, error} = await request(
        api.searchTasks({
          pageSize: 10,
          processInstanceKey: id,
          state: 'CREATED',
        }),
      );

      if (response !== null) {
        const data = await response.json();

        if (data.length === 0) {
          return;
        }

        newProcessInstance.removeInstance();

        if (
          data.length === 1 &&
          location.pathname === `/${pages.processes()}`
        ) {
          const [{id}] = data;

          tracking.track({
            eventName: 'process-tasks-polling-ended',
            outcome: 'single-task-found',
          });

          navigate({pathname: pages.taskDetails(id)});

          return;
        }

        return data;
      }

      if (error !== null) {
        throw error;
      }

      throw new Error('No tasks found');
    },
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  return null;
});

export {NewProcessInstanceTasksPolling};
