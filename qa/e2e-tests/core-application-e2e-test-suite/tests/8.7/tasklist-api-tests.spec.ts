import {test} from '@fixtures/8.7';
import {authAPI, assertResponseStatus} from 'utils/apiHelpers';
import {createInstances, deploy} from 'utils/zeebeClient';

const baseURL = process.env.CORE_APPLICATION_TASKLIST_URL;

test.beforeAll(async () => {
  await Promise.all([
    deploy('./resources/User_Task_Process_With_Form_API.bpmn'),
  ]);
  await createInstances('Form_User_Task_API', 1, 3);
  await authAPI('demo', 'demo', 'tasklist');
});

test.describe('API tests', () => {
  test.use({
    storageState: 'utils/.auth',
    baseURL: baseURL,
  });

  test('Search for tasks', async ({request}) => {
    const taskList = await request.post('/v1/tasks/search');
    await assertResponseStatus(taskList, 200);
  });

  test('Get a task via ID', async ({request}) => {
    const searchTasks = await request.post('/v1/tasks/search');
    const taskID = await searchTasks.json();
    const response = await request.get('/v1/tasks/' + taskID[1].id);
    await assertResponseStatus(response, 200);
  });
});
