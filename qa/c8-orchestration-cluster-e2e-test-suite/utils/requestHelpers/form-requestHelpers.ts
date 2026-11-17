/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIResponse, expect} from '@playwright/test';
import {APIRequestContext} from 'playwright-core';
import {buildUrl, jsonHeaders} from '../http';
import {deployInlineFiles, InlineResource} from './resource-requestHelpers';

export interface DeployedForm {
  formKey: string;
  formId: string;
  version: number;
  resourceName: string;
  schema: string;
}

export function formResourceContent(
  formId: string,
  label: string,
  versionTag?: string,
): string {
  return JSON.stringify({
    type: 'default',
    id: formId,
    ...(versionTag ? {versionTag} : {}),
    components: [{key: 'applicant', label, type: 'textfield'}],
  });
}

export async function deployInlineForms(
  request: APIRequestContext,
  forms: InlineResource[],
  extraFiles: InlineResource[] = [],
): Promise<DeployedForm[]> {
  const body = await deployInlineFiles(request, [...forms, ...extraFiles]);
  const deployed = (
    body.deployments as {
      form?: {
        formKey: string;
        formId: string;
        version: number;
        resourceName: string;
      };
    }[]
  )
    .filter((deployment) => deployment.form != null)
    .map((deployment) => {
      const form = deployment.form!;
      const source = forms.find((file) => file.fileName === form.resourceName);
      expect(
        source,
        `Deployed form ${form.resourceName} is not among the requested files`,
      ).toBeDefined();
      return {
        formKey: form.formKey,
        formId: form.formId,
        version: form.version,
        resourceName: form.resourceName,
        schema: String(source!.content),
      };
    });

  expect(deployed).toHaveLength(forms.length);
  return deployed;
}

export async function deployInlineForm(
  request: APIRequestContext,
  fileName: string,
  content: string,
): Promise<DeployedForm> {
  const [form] = await deployInlineForms(request, [{fileName, content}]);
  return form;
}

export function getFormByKey(
  request: APIRequestContext,
  formKey: string,
): Promise<APIResponse> {
  return request.get(buildUrl('/forms/{formKey}', {formKey}), {
    headers: jsonHeaders(),
  });
}
