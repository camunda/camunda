import {test} from '@fixtures/8.7';
import {expect} from 'playwright/test';
import {authAPI, assertResponseStatus} from 'utils/apiHelpers';
import {createInstances, deploy} from 'utils/zeebeClient';

const baseURL = process.env.CORE_APPLICATION_OPERATE_URL;

test.beforeAll(async () => {
  await Promise.all([
    deploy('./resources/User_Task_Process_With_Form_API.bpmn'),
  ]);
  await createInstances('Form_User_Task_API', 1, 3);
  await authAPI('demo', 'demo', 'operate');
});

test.describe('API tests', () => {
  test.use({
    storageState: 'utils/.auth',
    baseURL: baseURL,
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
});
