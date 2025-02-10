import {test, expect} from '@playwright/test';
import {auhtAPI, assertResponseStatus} from 'utils/apiHelpers';
import {createInstances, deploy} from 'utils/zeebeClient';

test.beforeAll(async () => {
  await Promise.all([deploy('./resources/User_Task_Process_With_Form.bpmn')]);
  await createInstances('Form_User_Task', 1, 3);
  await auhtAPI('demo', 'demo');
});

test.beforeEach(async ({context}) => {
  await context.storageState({path: 'utils/.auth'});
});

test('Search for process definitions', async ({request}) => {
  const processDefinitionsList = await request.post(
    '/v1/process-definitions/search',
  );
  await assertResponseStatus(processDefinitionsList, 200);
});

test('Get a process definition via key', async ({request}) => {
  const searchProcessDefinitions = await request.post(
    '/v1/process-definitions/search',
  );
  const processKey = await searchProcessDefinitions.json();
  const response = await request.get(
    '/v1/process-definitions/' + processKey.items[1].key,
  );
  await expect(response).toBeOK();
  await assertResponseStatus(response, 200);
});

test('Search for process instances', async ({request}) => {
  const processInstancesList = await request.post(
    'v1/process-instances/search',
  );
  await assertResponseStatus(processInstancesList, 200);
});

test('Search for flownode-instances', async ({request}) => {
  const flowNodeInstancesList = await request.post(
    'v1/flownode-instances/search',
  );
  await assertResponseStatus(flowNodeInstancesList, 200);
});

test('Search for variables for process instancess', async ({request}) => {
  const variablesInstancesList = await request.post('v1/variables/search');
  await assertResponseStatus(variablesInstancesList, 200);
});

test('Search for incidents', async ({request}) => {
  const incidentsList = await request.post('v1/incidents/search');
  await assertResponseStatus(incidentsList, 200);
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
