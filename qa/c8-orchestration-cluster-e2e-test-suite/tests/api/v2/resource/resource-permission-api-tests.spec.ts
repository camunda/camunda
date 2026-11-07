/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, APIResponse, expect, test} from '@playwright/test';
import {
  assertForbiddenRequest,
  assertStatusCode,
  buildUrl,
  defaultHeaders,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
  generateUniqueProcessDefinitionId,
} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {cleanupResources} from '../../../../utils/resourceCleanup';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  CREATE_NEW_USER,
} from '../../../../utils/beans/requestBeans';
import {
  createComponentAuthorization,
  createUser,
  DELETE_RESOURCE_STATUS,
  deleteResource,
  expectProcessDefinitionState,
  formResourceContent,
  deployInlineFiles,
  deployInlineFilesRaw,
  DeploymentEntry,
  getResource,
  getResourceContent,
  InlineResource,
  inlineBpmnProcess,
  inlineDmnDecision,
  inlineRpaResource,
  ResourceMetadata,
  resourceMetadataOf,
} from '@requestHelpers';

type TestUser = ReturnType<typeof CREATE_NEW_USER>;

type Permission =
  | 'CREATE'
  | 'READ'
  | 'DELETE_PROCESS'
  | 'DELETE_DRD'
  | 'DELETE_FORM'
  | 'DELETE_RESOURCE';

const forbiddenDetail = (permission: Permission) =>
  `Insufficient permissions to perform operation '${permission}' on resource 'RESOURCE'`;

type Deployed = ResourceMetadata & {resourceId: string};

// One deployment for all fixtures of a group. A DMN file yields a decision entry
// next to its decision requirements entry; only the latter carries the key
// POST /resources/:key/deletion takes, so decision entries are dropped.
async function deployAll(
  request: APIRequestContext,
  files: InlineResource[],
): Promise<ResourceMetadata[]> {
  const {deployments} = (await deployInlineFiles(request, files)) as {
    deployments: DeploymentEntry[];
  };
  return deployments
    .filter((deployment) => !deployment.decisionDefinition)
    .map(resourceMetadataOf);
}

function deployedAs(
  deployed: ResourceMetadata[],
  file: InlineResource,
): Deployed {
  const match = deployed.find((d) => d.resourceName === file.fileName);
  expect(
    match?.resourceId,
    `${file.fileName} is missing from the deployment`,
  ).toBeDefined();
  return match as Deployed;
}

type DeletableType = {
  name: string;
  permission: Permission;
  file: () => InlineResource;
  expectStillPresent: (
    request: APIRequestContext,
    resourceKey: string,
  ) => Promise<void>;
};

const PROCESS: DeletableType = {
  name: 'process definition',
  permission: 'DELETE_PROCESS',
  file: () => inlineBpmnProcess(generateUniqueProcessDefinitionId()),
  expectStillPresent: (request, processDefinitionKey) =>
    expectProcessDefinitionState(request, processDefinitionKey, 'ACTIVE'),
};

const DECISION_REQUIREMENTS: DeletableType = {
  name: 'decision requirements',
  permission: 'DELETE_DRD',
  file: () => inlineDmnDecision(`drd_${generateUniqueId()}`),
  expectStillPresent: (request, decisionRequirementsKey) =>
    expect(async () => {
      const res = await request.get(
        buildUrl('/decision-requirements/{decisionRequirementsKey}', {
          decisionRequirementsKey,
        }),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(res, 200);
    }).toPass(defaultAssertionOptions),
};

const FORM: DeletableType = {
  name: 'form',
  permission: 'DELETE_FORM',
  file: () => {
    const formId = `form_${generateUniqueId()}`;
    return {
      fileName: `${formId}.form`,
      content: formResourceContent(formId, 'permission test'),
    };
  },
  // There is no GET /forms/:key; an admin deletion succeeding proves the form
  // was still there.
  expectStillPresent: async (request, formKey) =>
    assertStatusCode(
      await deleteResource(request, formKey),
      DELETE_RESOURCE_STATUS,
    ),
};

const RPA: DeletableType = {
  name: 'RPA resource',
  permission: 'DELETE_RESOURCE',
  file: inlineRpaResource,
  expectStillPresent: (request, resourceKey) =>
    expect(async () => {
      await assertStatusCode(await getResource(request, resourceKey), 200);
    }).toPass(defaultAssertionOptions),
};

// A new user answers 401 until it is indexed. Retry only that, so a command
// that should be rejected is judged on its first real answer instead of being
// resent until the assertion happens to pass.
async function firstAuthenticatedResponse(
  send: () => Promise<APIResponse>,
): Promise<APIResponse> {
  let res!: APIResponse;
  await expect(async () => {
    res = await send();
    expect(res.status()).not.toBe(401);
  }).toPass(defaultAssertionOptions);
  return res;
}

function tokenOf(user: TestUser): string {
  return encode(`${user.username}:${user.password}`);
}

test.describe('Resource Permission API', () => {
  const usernames: string[] = [];
  const resourceKeysToCleanUp: string[] = [];

  async function createUserWith(
    request: APIRequestContext,
    permission: Permission,
    resourceId: string,
  ): Promise<string> {
    const user = await createUser(request);
    usernames.push(user.username);
    await createComponentAuthorization(
      request,
      CREATE_CUSTOM_AUTHORIZATION_BODY(
        user.username,
        'USER',
        resourceId,
        'RESOURCE',
        [permission],
      ),
    );
    return tokenOf(user);
  }

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, usernames);
    await cleanupResources(request, resourceKeysToCleanUp);
  });

  test.describe('Forbidden without permission', () => {
    const deletions = [PROCESS, DECISION_REQUIREMENTS, FORM, RPA];
    const targets = new Map<DeletableType, Deployed>();
    let rpa: Deployed;
    let userWithoutPermissionsToken: string;

    test.beforeAll(async ({request}) => {
      const files = deletions.map((type) => type.file());
      const deployed = await deployAll(request, files);
      deletions.forEach((type, i) =>
        targets.set(type, deployedAs(deployed, files[i])),
      );
      resourceKeysToCleanUp.push(
        ...[...targets.values()].map((target) => target.resourceKey),
      );
      rpa = targets.get(RPA)!;

      // No authorization at all: every RESOURCE permission is missing.
      const user = await createUser(request);
      usernames.push(user.username);
      userWithoutPermissionsToken = tokenOf(user);
    });

    // eslint-disable-next-line playwright/expect-expect
    test('Deploy resources - forbidden without CREATE permission', async ({
      request,
    }) => {
      // given
      const resource = inlineRpaResource();

      // when
      const res = await firstAuthenticatedResponse(() =>
        deployInlineFilesRaw(
          request,
          [resource],
          defaultHeaders(userWithoutPermissionsToken),
        ),
      );

      // then
      // POST /deployments requires CREATE on RESOURCE '*'; the command is rejected.
      await assertForbiddenRequest(res, forbiddenDetail('CREATE'));
    });

    for (const type of deletions) {
      // eslint-disable-next-line playwright/expect-expect
      test(`Delete ${type.name} - forbidden without ${type.permission} permission`, async ({
        request,
      }) => {
        // given
        const {resourceKey} = targets.get(type)!;

        // when
        const res = await firstAuthenticatedResponse(() =>
          deleteResource(request, resourceKey, {
            headers: jsonHeaders(userWithoutPermissionsToken),
          }),
        );

        // then
        // Deleting it requires the type-specific DELETE_* permission on RESOURCE; the command is rejected.
        await assertForbiddenRequest(res, forbiddenDetail(type.permission));

        // and the rejected deletion left the resource in place
        await type.expectStillPresent(request, resourceKey);
      });
    }

    // eslint-disable-next-line playwright/expect-expect
    test('Get resource - forbidden without READ permission', async ({
      request,
    }) => {
      // given
      const {resourceKey} = rpa;

      // when
      const res = await firstAuthenticatedResponse(() =>
        getResource(request, resourceKey, {
          headers: jsonHeaders(userWithoutPermissionsToken),
        }),
      );

      // then
      // GET /resources/:key requires READ on RESOURCE; it rejects rather than filters.
      await assertForbiddenRequest(res, forbiddenDetail('READ'));
    });

    // eslint-disable-next-line playwright/expect-expect
    test('Get resource content - forbidden without READ permission', async ({
      request,
    }) => {
      // given
      const {resourceKey} = rpa;

      // when
      const res = await firstAuthenticatedResponse(() =>
        getResourceContent(request, resourceKey, {
          headers: jsonHeaders(userWithoutPermissionsToken),
        }),
      );

      // then
      // GET /resources/:key/content requires READ on RESOURCE; it rejects rather than filters.
      await assertForbiddenRequest(res, forbiddenDetail('READ'));
    });
  });

  test.describe('Allowed with the required permission', () => {
    const deletionGrants = [PROCESS, DECISION_REQUIREMENTS, FORM, RPA];
    const grantTargets = new Map<
      DeletableType,
      {granted: Deployed; outside: Deployed}
    >();
    let readable: Deployed;
    let other: Deployed;

    test.beforeAll(async ({request}) => {
      const readableFile = inlineRpaResource();
      const otherFile = inlineRpaResource();
      const deletionFiles = deletionGrants.map((type) => ({
        granted: type.file(),
        outside: type.file(),
      }));
      const deployed = await deployAll(request, [
        readableFile,
        otherFile,
        ...deletionFiles.flatMap(({granted, outside}) => [granted, outside]),
      ]);
      resourceKeysToCleanUp.push(...deployed.map((d) => d.resourceKey));
      readable = deployedAs(deployed, readableFile);
      other = deployedAs(deployed, otherFile);
      deletionGrants.forEach((type, i) =>
        grantTargets.set(type, {
          granted: deployedAs(deployed, deletionFiles[i].granted),
          outside: deployedAs(deployed, deletionFiles[i].outside),
        }),
      );
    });

    test('Deploy resources - succeeds with CREATE permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'CREATE', '*');
      const resource = inlineRpaResource();

      await expect(async () => {
        // when
        const res = await deployInlineFilesRaw(
          request,
          [resource],
          defaultHeaders(token),
        );

        // then
        await assertStatusCode(res, 200);
        const body = await res.json();
        resourceKeysToCleanUp.push(body.deployments[0].resource.resourceKey);
        expect(body.deployments[0].resource.resourceName).toBe(
          resource.fileName,
        );
      }).toPass(defaultAssertionOptions);
    });

    for (const type of deletionGrants) {
      test(`Delete ${type.name} - allowed only on the ${type.name} ${type.permission} is granted on`, async ({
        request,
      }) => {
        // given
        const {granted, outside} = grantTargets.get(type)!;
        const token = await createUserWith(
          request,
          type.permission,
          granted.resourceId,
        );

        await expect(async () => {
          // when
          const res = await deleteResource(request, granted.resourceKey, {
            headers: jsonHeaders(token),
          });

          // then
          await assertStatusCode(res, DELETE_RESOURCE_STATUS);
        }).toPass(defaultAssertionOptions);

        // when
        const res = await deleteResource(request, outside.resourceKey, {
          headers: jsonHeaders(token),
        });

        // then
        // The DELETE_* grant is scoped to one resource id; another resource of
        // the same type is rejected and stays in place.
        await assertForbiddenRequest(res, forbiddenDetail(type.permission));
        await type.expectStillPresent(request, outside.resourceKey);
      });
    }

    test('Get resource - allowed only on the resource READ is granted on', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'READ', readable.resourceId);

      await expect(async () => {
        // when
        const res = await getResource(request, readable.resourceKey, {
          headers: jsonHeaders(token),
        });

        // then
        await assertStatusCode(res, 200);
        expect((await res.json()).resourceKey).toBe(readable.resourceKey);
      }).toPass(defaultAssertionOptions);

      // when
      const res = await getResource(request, other.resourceKey, {
        headers: jsonHeaders(token),
      });

      // then
      // READ is scoped to one resourceId; any other resource is rejected.
      await assertForbiddenRequest(res, forbiddenDetail('READ'));
    });

    test('Get resource content - allowed only on the resource READ is granted on', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'READ', readable.resourceId);

      await expect(async () => {
        // when
        const res = await getResourceContent(request, readable.resourceKey, {
          headers: jsonHeaders(token),
        });

        // then
        await assertStatusCode(res, 200);
        expect((await res.json()).id).toBe(readable.resourceId);
      }).toPass(defaultAssertionOptions);

      // when
      const res = await getResourceContent(request, other.resourceKey, {
        headers: jsonHeaders(token),
      });

      // then
      // READ is scoped to one resourceId; any other resource is rejected.
      await assertForbiddenRequest(res, forbiddenDetail('READ'));
    });
  });
});
