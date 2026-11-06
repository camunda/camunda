/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {
  assertForbiddenRequest,
  assertStatusCode,
  authHeaders,
  buildUrl,
  encode,
  jsonHeaders,
  octetStreamHeaders,
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
  deleteResource,
  expectProcessDefinitionState,
  formResourceContent,
  getFormByKey,
  deployInlineFiles,
  deployInlineFilesRaw,
  deployInlineResource,
  DeployedResource,
  getResource,
  getResourceContent,
  getResourceContentBinary,
  InlineResource,
  inlineBpmnProcess,
  inlineDmnDecision,
  inlineGenericResource,
  inlineRpaResource,
  ResourceMetadata,
  resourceKeysOf,
  resourceMetadataOf,
  searchResources,
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
const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ' on resource 'RESOURCE'";
const GENERIC_PREFIX = 'permission-test';

// A DMN deployment lists its decisions before its decision requirements; the
// latter carries the key POST /resources/:key/deletion takes.
async function deployModel(
  request: APIRequestContext,
  resource: InlineResource,
): Promise<ResourceMetadata> {
  const {deployments} = (await deployInlineFiles(request, [resource])) as {
    deployments: Record<string, unknown>[];
  };
  return resourceMetadataOf(
    deployments.find((d) => d.decisionRequirements) ?? deployments[0],
  );
}

async function deployFile(
  request: APIRequestContext,
  {fileName, content}: InlineResource,
): Promise<DeployedResource> {
  return deployInlineResource(request, fileName, content);
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
    let processDefinition: ResourceMetadata;
    let decisionRequirements: ResourceMetadata;
    let deployedForm: ResourceMetadata;
    let generic: DeployedResource;
    let rpa: DeployedResource;
    let userWithoutPermissionsToken: string;

    test.beforeAll(async ({request}) => {
      processDefinition = await deployModel(
        request,
        inlineBpmnProcess(generateUniqueProcessDefinitionId()),
      );
      decisionRequirements = await deployModel(
        request,
        inlineDmnDecision(`drd_${generateUniqueId()}`),
      );
      const formId = `form_${generateUniqueId()}`;
      deployedForm = await deployModel(request, {
        fileName: `${formId}.form`,
        content: formResourceContent(formId, 'permission test'),
      });
      generic = await deployFile(
        request,
        inlineGenericResource(GENERIC_PREFIX),
      );
      rpa = await deployFile(request, inlineRpaResource());
      resourceKeysToCleanUp.push(
        processDefinition.resourceKey,
        decisionRequirements.resourceKey,
        deployedForm.resourceKey,
        generic.resourceKey,
        rpa.resourceKey,
      );

      // No authorization at all: every RESOURCE permission is missing.
      const user = await createUser(request);
      usernames.push(user.username);
      userWithoutPermissionsToken = tokenOf(user);
    });

    test('Deploy resources - forbidden without CREATE permission', async ({
      request,
    }) => {
      // given
      const resource = inlineGenericResource(GENERIC_PREFIX);

      await expect(async () => {
        // when
        const res = await deployInlineFilesRaw(request, [resource], {
          Accept: 'application/json',
          ...authHeaders(userWithoutPermissionsToken),
        });

        // then
        // POST /deployments requires CREATE on RESOURCE '*'; the command is rejected.
        await assertForbiddenRequest(res, forbiddenDetail('CREATE'));
      }).toPass(defaultAssertionOptions);
    });

    const deletions: {
      name: string;
      permission: Permission;
      target: () => {resourceKey: string};
      expectStillPresent: (
        request: APIRequestContext,
        resourceKey: string,
      ) => Promise<void>;
    }[] = [
      {
        name: 'process definition',
        permission: 'DELETE_PROCESS',
        target: () => processDefinition,
        expectStillPresent: (request, processDefinitionKey) =>
          expectProcessDefinitionState(request, processDefinitionKey, 'ACTIVE'),
      },
      {
        name: 'decision requirements',
        permission: 'DELETE_DRD',
        target: () => decisionRequirements,
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
      },
      {
        name: 'form',
        permission: 'DELETE_FORM',
        target: () => deployedForm,
        expectStillPresent: (request, formKey) =>
          expect(async () => {
            await assertStatusCode(await getFormByKey(request, formKey), 200);
          }).toPass(defaultAssertionOptions),
      },
      {
        name: 'generic resource',
        permission: 'DELETE_RESOURCE',
        target: () => generic,
        expectStillPresent: (request, resourceKey) =>
          expect(async () => {
            await assertStatusCode(
              await getResource(request, resourceKey),
              200,
            );
          }).toPass(defaultAssertionOptions),
      },
      {
        name: 'RPA resource',
        permission: 'DELETE_RESOURCE',
        target: () => rpa,
        expectStillPresent: (request, resourceKey) =>
          expect(async () => {
            await assertStatusCode(
              await getResource(request, resourceKey),
              200,
            );
          }).toPass(defaultAssertionOptions),
      },
    ];

    for (const {name, permission, target, expectStillPresent} of deletions) {
      test(`Delete ${name} - forbidden without ${permission} permission`, async ({
        request,
      }) => {
        // given
        const {resourceKey} = target();

        await expect(async () => {
          // when
          const res = await deleteResource(request, resourceKey, {
            headers: jsonHeaders(userWithoutPermissionsToken),
          });

          // then
          // Deleting it requires the type-specific DELETE_* permission on RESOURCE; the command is rejected.
          await assertForbiddenRequest(res, forbiddenDetail(permission));
        }).toPass(defaultAssertionOptions);

        // and the rejected deletion left the resource in place
        await expectStillPresent(request, resourceKey);
      });
    }

    test('Get resource - forbidden without READ permission', async ({
      request,
    }) => {
      // given
      const {resourceKey} = generic;

      await expect(async () => {
        // when
        const res = await getResource(request, resourceKey, {
          headers: jsonHeaders(userWithoutPermissionsToken),
        });

        // then
        // GET /resources/:key requires READ on RESOURCE; it rejects rather than filters.
        await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
      }).toPass(defaultAssertionOptions);
    });

    test('Get resource content - forbidden without READ permission', async ({
      request,
    }) => {
      // given
      const {resourceKey} = rpa;

      await expect(async () => {
        // when
        const res = await getResourceContent(request, resourceKey, {
          headers: jsonHeaders(userWithoutPermissionsToken),
        });

        // then
        // GET /resources/:key/content requires READ on RESOURCE; it rejects rather than filters.
        await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
      }).toPass(defaultAssertionOptions);
    });

    test('Get resource content binary - forbidden without READ permission', async ({
      request,
    }) => {
      // given
      const {resourceKey} = generic;

      await expect(async () => {
        // when
        const res = await getResourceContentBinary(request, resourceKey, {
          headers: octetStreamHeaders(userWithoutPermissionsToken),
        });

        // then
        // GET /resources/:key/content/binary requires READ on RESOURCE; it rejects rather than filters.
        await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
      }).toPass(defaultAssertionOptions);
    });

    test('Search resources - filtered out without READ permission', async ({
      request,
    }) => {
      // given
      const filter = {filter: {resourceKey: generic.resourceKey}};

      await expect(async () => {
        const adminRes = await searchResources(request, filter);
        await assertStatusCode(adminRes, 200);
        expect(resourceKeysOf(await adminRes.json())).toEqual([
          generic.resourceKey,
        ]);

        // when
        const res = await searchResources(request, filter, {
          headers: jsonHeaders(userWithoutPermissionsToken),
        });

        // then
        // POST /resources/search requires READ on RESOURCE; unauthorized resources
        // are filtered from the result instead of rejected.
        await assertStatusCode(res, 200);
        const body = await res.json();
        expect(body.items).toHaveLength(0);
        expect(body.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test.describe('Allowed with the required permission', () => {
    let readable: DeployedResource;
    let readableRpa: DeployedResource;
    let deletable: DeployedResource;

    test.beforeAll(async ({request}) => {
      readable = await deployFile(
        request,
        inlineGenericResource(GENERIC_PREFIX),
      );
      deletable = await deployFile(
        request,
        inlineGenericResource(GENERIC_PREFIX),
      );
      readableRpa = await deployFile(request, inlineRpaResource());
      resourceKeysToCleanUp.push(
        readable.resourceKey,
        readableRpa.resourceKey,
        deletable.resourceKey,
      );
    });

    test('Deploy resources - succeeds with CREATE permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'CREATE', '*');
      const resource = inlineGenericResource(GENERIC_PREFIX);

      await expect(async () => {
        // when
        const res = await deployInlineFilesRaw(request, [resource], {
          Accept: 'application/json',
          ...authHeaders(token),
        });

        // then
        await assertStatusCode(res, 200);
        const body = await res.json();
        resourceKeysToCleanUp.push(body.deployments[0].resource.resourceKey);
        expect(body.deployments[0].resource.resourceName).toBe(
          resource.fileName,
        );
      }).toPass(defaultAssertionOptions);
    });

    test('Delete resource - succeeds with DELETE_RESOURCE permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(
        request,
        'DELETE_RESOURCE',
        deletable.resourceId,
      );

      await expect(async () => {
        // when
        const res = await deleteResource(request, deletable.resourceKey, {
          headers: jsonHeaders(token),
        });

        // then
        await assertStatusCode(res, 200);
      }).toPass(defaultAssertionOptions);
    });

    test('Get resource - succeeds with READ permission', async ({request}) => {
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
    });

    test('Get resource content - succeeds with READ permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(
        request,
        'READ',
        readableRpa.resourceId,
      );

      await expect(async () => {
        // when
        const res = await getResourceContent(request, readableRpa.resourceKey, {
          headers: jsonHeaders(token),
        });

        // then
        await assertStatusCode(res, 200);
        expect((await res.json()).id).toBe(readableRpa.resourceId);
      }).toPass(defaultAssertionOptions);
    });

    test('Get resource content binary - succeeds with READ permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'READ', readable.resourceId);

      await expect(async () => {
        // when
        const res = await getResourceContentBinary(
          request,
          readable.resourceKey,
          {headers: octetStreamHeaders(token)},
        );

        // then
        await assertStatusCode(res, 200);
        expect((await res.body()).toString('utf-8')).toBe(
          `# ${GENERIC_PREFIX}`,
        );
      }).toPass(defaultAssertionOptions);
    });

    test('Search resources - returns the resource with READ permission', async ({
      request,
    }) => {
      // given
      const token = await createUserWith(request, 'READ', readable.resourceId);

      await expect(async () => {
        // when
        const res = await searchResources(
          request,
          {filter: {resourceKey: readable.resourceKey}},
          {headers: jsonHeaders(token)},
        );

        // then
        await assertStatusCode(res, 200);
        expect(resourceKeysOf(await res.json())).toEqual([
          readable.resourceKey,
        ]);
      }).toPass(defaultAssertionOptions);
    });
  });
});
