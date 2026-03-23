/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {buildUrl, jsonHeaders} from 'utils/http';
import {
  createUser,
  createComponentAuthorization,
  cleanupAuthorizations,
  findUserTask,
} from '@requestHelpers';
import {cleanupUsers} from 'utils/usersCleanup';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  CREATE_COMPONENT_AUTHORIZATION,
} from 'utils/beans/requestBeans';

const PROCESS_ID = 'user_task_api_test_process';
const TASK_NAME = 'test user task api';

let aliceUser: {
  username: string;
  name: string;
  email: string;
  password: string;
};
let bobUser: {
  username: string;
  name: string;
  email: string;
  password: string;
};
let userTaskKey: string;
let bobUserTaskKey: string;
const authorizationKeys: string[] = [];
const createdUsernames: string[] = [];

test.describe.serial('Task visible to assignee with READ permission', () => {
  test.beforeAll(async ({request}) => {
    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    const componentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
    );
    authorizationKeys.push(componentAuthKey);

    const userTaskAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        aliceUser.username,
        'USER',
        '*',
        'USER_TASK',
        ['READ'],
      ),
    );
    authorizationKeys.push(userTaskAuthKey);

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');

    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
      {
        headers: jsonHeaders(),
        data: {assignee: aliceUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should display assigned task in tasklist for user with READ permission on assignee', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');

    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.filterBy('Assigned to me');

    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText(TASK_NAME),
      ).toBeVisible();
    }).toPass({timeout: 30000});

    await taskPanelPage.openTask(TASK_NAME);

    await expect(taskDetailsPage.assignee).toContainText('Assigned to me');
  });
});

test.describe
  .serial('Assignee cannot see their task without READ permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
      {
        headers: jsonHeaders(),
        data: {assignee: aliceUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should not display the assigned task without READ permission', async ({
    page,
    loginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.filterBy('Assigned to me');

    await expect(async () => {
      await expect(page.getByText('No tasks found')).toBeVisible();
      await expect(
        taskPanelPage.availableTasks.getByText(TASK_NAME),
      ).toBeHidden();
    }).toPass({timeout: 30000});
  });
});

test.describe.serial('Task not visible to user without READ permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    bobUser = await createUser(request);
    createdUsernames.push(bobUser.username);

    const bobComponentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', bobUser.username),
    );
    authorizationKeys.push(bobComponentAuthKey);

    const bobTaskAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        bobUser.username,
        'USER',
        '*',
        'USER_TASK',
        ['READ'],
      ),
    );
    authorizationKeys.push(bobTaskAuthKey);

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    const aliceComponentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
    );
    authorizationKeys.push(aliceComponentAuthKey);

    bobUserTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: bobUserTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: bobUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should not display task assigned to Bob when Alice has no READ permission', async ({
    page,
    loginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.filterBy('Assigned to me');

    await expect(async () => {
      await expect(page.getByText('No tasks found')).toBeVisible();
      await expect(
        taskPanelPage.availableTasks.getByText(TASK_NAME),
      ).toBeHidden();
    }).toPass({timeout: 30000});
  });

  test('should not show task details when Alice opens task URL directly', async ({
    page,
    loginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(bobUserTaskKey);

    await expect(page.getByTestId('details-info')).toBeHidden({
      timeout: 10000,
    });
  });
});

test.describe
  .serial('User with UPDATE_USER_TASK permission can claim an unassigned task', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    const componentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
    );
    authorizationKeys.push(componentAuthKey);

    const readAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        aliceUser.username,
        'USER',
        '*',
        'USER_TASK',
        ['READ'],
      ),
    );
    authorizationKeys.push(readAuthKey);

    const claimAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        aliceUser.username,
        'USER',
        '*',
        'PROCESS_DEFINITION',
        ['UPDATE_USER_TASK'],
      ),
    );
    authorizationKeys.push(claimAuthKey);

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should allow Alice to claim an unassigned task with UPDATE_USER_TASK permission', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(userTaskKey);

    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.assignee).toContainText('Assigned to me');
  });
});

test.describe
  .serial('User cannot claim a task without UPDATE_USER_TASK permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['READ'],
        ),
      ),
    );

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should not allow Alice to claim an unassigned task without UPDATE_USER_TASK permission', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(userTaskKey);

    await expect(taskDetailsPage.assignToMeButton).toBeHidden();
  });
});

test.describe
  .serial('User can override task assignment with UPDATE permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    bobUser = await createUser(request);
    createdUsernames.push(bobUser.username);

    const bobComponentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', bobUser.username),
    );
    authorizationKeys.push(bobComponentAuthKey);

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    const aliceComponentAuthKey = await createComponentAuthorization(
      request,
      CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
    );
    authorizationKeys.push(aliceComponentAuthKey);

    const readAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        aliceUser.username,
        'USER',
        '*',
        'USER_TASK',
        ['READ'],
      ),
    );
    authorizationKeys.push(readAuthKey);

    const updateAuthKey = await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        aliceUser.username,
        'USER',
        '*',
        'PROCESS_DEFINITION',
        ['UPDATE_USER_TASK'],
      ),
    );
    authorizationKeys.push(updateAuthKey);

    bobUserTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: bobUserTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: bobUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should allow Alice to reassign a task from Bob when Alice has UPDATE permission', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(bobUserTaskKey);

    await taskDetailsPage.clickUnassignButton();

    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.assignee).toContainText('Assigned to me');
  });
});

test.describe
  .serial('User is prevented from completing a task if not the assignee', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    bobUser = await createUser(request);
    createdUsernames.push(bobUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', bobUser.username),
      ),
    );

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['READ'],
        ),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['COMPLETE'],
        ),
      ),
    );

    bobUserTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: bobUserTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: bobUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should prevent Alice from completing a task assigned to Bob', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(bobUserTaskKey);

    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
  });
});

test.describe
  .serial('User can complete a task when they have COMPLETE permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['READ'],
        ),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['COMPLETE'],
        ),
      ),
    );

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: userTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: aliceUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should allow Alice to complete a task she is assigned to with COMPLETE permission', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(userTaskKey);

    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();

    await taskDetailsPage.clickCompleteTaskButton();

    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
  });
});

test.describe
  .serial('User can unassign themselves from a task with UPDATE_USER_TASK permission', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'USER_TASK',
          ['READ'],
        ),
      ),
    );

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_CUSTOM_AUTHORIZATION_BODY(
          aliceUser.username,
          'USER',
          '*',
          'PROCESS_DEFINITION',
          ['UPDATE_USER_TASK'],
        ),
      ),
    );

    userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: userTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: aliceUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should allow Alice to unassign herself from a task with UPDATE_USER_TASK permission', async ({
    page,
    loginPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.goToTaskDetails(userTaskKey);

    await taskDetailsPage.clickUnassignButton();

    await expect(taskDetailsPage.assignee).toContainText('Unassigned');
  });
});
