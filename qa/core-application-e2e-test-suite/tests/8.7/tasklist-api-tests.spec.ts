import {test} from '@fixtures/8.7';
import {expect} from '@playwright/test';
import {authAPI} from 'utils/apiHelpers';
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
    expect(taskList.status()).toBe(200);
  });

  test('Get a task via ID', async ({request}) => {
    const searchTasks = await request.post('/v1/tasks/search');
    const taskID = await searchTasks.json();
    const response = await request.get('/v1/tasks/' + taskID[1].id);
    expect(response.status()).toBe(200);
  });
});
