/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getClientConfig} from 'common/config/getClientConfig';
import {mergePathname} from './mergePathname';
import {identity, management} from '@vzeta/camunda-api-zod-schemas';
import {buildDocumentMultipart} from 'common/document-handling/buildDocumentMultipart';

const BASE_REQUEST_OPTIONS: RequestInit = {
  credentials: 'include',
  mode: 'cors',
};

function getFullURL(url: string) {
  if (typeof window.location.origin !== 'string') {
    throw new Error('window.location.origin is not a set');
  }

  return new URL(
    mergePathname(getClientConfig().contextPath, url),
    window.location.origin,
  );
}

const commonApi = {
  login: (body: {username: string; password: string}) => {
    return new Request(getFullURL('/login'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: new URLSearchParams(body).toString(),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
  },
  logout: () =>
    new Request(getFullURL('/logout'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  getCurrentUser: () =>
    new Request(getFullURL(identity.endpoints.getCurrentUser.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: identity.endpoints.getCurrentUser.method,
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  getLicense: () => {
    return new Request(getFullURL(management.endpoints.getLicense.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: management.endpoints.getLicense.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  uploadDocuments: ({files}: {files: Map<string, File[]>}) => {
    const {body, headers} = buildDocumentMultipart(files);

    return new Request(getFullURL('/v2/documents/batch'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body,
      headers,
    });
  },
  getDocument: (id: string) => {
    return new Request(getFullURL(`/v2/documents/${id}`), {
      method: 'GET',
      ...BASE_REQUEST_OPTIONS,
    });
  },
};

export {commonApi, getFullURL, BASE_REQUEST_OPTIONS};
