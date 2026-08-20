/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  deployInlineForm,
  formResourceContent,
  getFormByKey,
  uniqueResourceName,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

test.describe.parallel('Form Get API', () => {
  test('Get Form - Success 200', async ({request}) => {
    const formId = `approval-form-${generateUniqueId()}`;
    const schema = formResourceContent(formId, 'Applicant name');
    const form = await deployInlineForm(
      request,
      uniqueResourceName('approval', 'form'),
      schema,
    );

    await expect(async () => {
      const res = await getFormByKey(request, form.formKey);

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/forms/{formKey}', method: 'GET', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body).toMatchObject({
        formKey: form.formKey,
        formId,
        version: 1,
        tenantId: '<default>',
      });
      expect(JSON.parse(body.schema)).toEqual(JSON.parse(schema));
    }).toPass(defaultAssertionOptions);
  });

  test('Get Form - Each Version Returns Its Own Schema', async ({request}) => {
    const formId = `versioned-form-${generateUniqueId()}`;
    const fileName = uniqueResourceName('versioned', 'form');
    const firstSchema = formResourceContent(formId, 'Applicant name');
    const secondSchema = formResourceContent(formId, 'Full legal name');

    const firstVersion = await deployInlineForm(request, fileName, firstSchema);
    const secondVersion = await deployInlineForm(
      request,
      fileName,
      secondSchema,
    );
    expect(secondVersion.version).toBe(2);

    await expect(async () => {
      const firstRes = await getFormByKey(request, firstVersion.formKey);
      await assertStatusCode(firstRes, 200);
      const firstBody = await firstRes.json();
      expect(firstBody.version).toBe(1);
      expect(JSON.parse(firstBody.schema)).toEqual(JSON.parse(firstSchema));

      const secondRes = await getFormByKey(request, secondVersion.formKey);
      await assertStatusCode(secondRes, 200);
      const secondBody = await secondRes.json();
      expect(secondBody.version).toBe(2);
      expect(JSON.parse(secondBody.schema)).toEqual(JSON.parse(secondSchema));
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Form - Not Found 404', async ({request}) => {
    const nonExistentFormKey = '2251799813733053';

    const res = await getFormByKey(request, nonExistentFormKey);

    await assertNotFoundRequest(
      res,
      `Form with key '${nonExistentFormKey}' not found`,
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Form - Bad Request 400 - Invalid formKey Format', async ({
    request,
  }) => {
    const res = await getFormByKey(request, 'invalid-string-key');

    await assertBadRequest(
      res,
      "Failed to convert 'formKey' with value: 'invalid-string-key'",
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Form - Unauthorized 401', async ({request}) => {
    const res = await request.get(
      buildUrl('/forms/{formKey}', {formKey: 'someKey'}),
      {headers: {}},
    );

    await assertUnauthorizedRequest(res);
  });
});
