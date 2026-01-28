/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {commonApi} from 'common/api';
import {request} from 'common/api/request';
import {PICKER_KEY} from 'common/document-handling/buildDocumentMultipart';
import type {
  DocumentReference,
  CreateDocumentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';

type Payload = {
  files: Map<string, File[]>;
};

const MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE = 207;

function useUploadDocuments() {
  return useMutation({
    mutationFn: async ({files}: Payload) => {
      const {response, error} = await request(
        commonApi.uploadDocuments({files}),
      );

      if (error !== null) {
        throw error;
      }

      if (response.status === MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE) {
        throw new Error('Failed to upload some documents');
      }

      const payload: CreateDocumentsResponseBody = await response.json();

      const result = new Map<string, DocumentReference[]>();

      payload.createdDocuments.forEach((document) => {
        const pickerKey = document.metadata.customProperties?.[PICKER_KEY];

        if (typeof pickerKey !== 'string' || pickerKey.length === 0) {
          return;
        }

        const documentResult = result.get(pickerKey);

        if (Array.isArray(documentResult)) {
          result.set(pickerKey, [...documentResult, document]);
        } else {
          result.set(pickerKey, [document]);
        }
      });

      return result;
    },
  });
}

export {useUploadDocuments};
