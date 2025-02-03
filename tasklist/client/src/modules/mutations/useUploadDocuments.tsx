/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'modules/api';
import {type RequestError, request} from 'modules/request';

type Payload = {
  files: Map<string, File[]>;
};

type FileUploadMetadata = {
  contentType: string;
  fileName: string;
  expiresAt?: string;
  size: number;
  processDefinitionId?: string;
  processInstanceKey?: number;
  customProperties?: Record<string, unknown>;
};

type SuccessDocument = {
  'camunda.document.type': 'camunda';
  storeId: string;
  documentId: string;
  contentHash: string;
  metadata: FileUploadMetadata;
};

type FailedDocument = {
  filename: string;
  detail: string;
};

type BatchUploadResponse = {
  createdDocuments: SuccessDocument[];
  failedDocuments: FailedDocument[];
};

const MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE = 207;

function useUploadDocuments() {
  return useMutation<
    Map<string, SuccessDocument[]>,
    RequestError | Error,
    Payload
  >({
    mutationFn: async ({files}) => {
      const fileGroupRanges = new Map<string, [number, number]>();
      const formattedFilePayload: File[] = [];
      const fileGroups = Array.from(files.entries());

      fileGroups.forEach(([key, files]) => {
        fileGroupRanges.set(key, [
          formattedFilePayload.length,
          formattedFilePayload.length + files.length - 1,
        ]);
        formattedFilePayload.push(...files);
      });

      const {response, error} = await request(
        api.v2.uploadDocuments({files: formattedFilePayload}),
      );

      if (error !== null) {
        throw error;
      }

      if (response.status === MIXED_SUCCESS_AND_FAILED_DOCUMENTS_STATUS_CODE) {
        throw new Error('Failed to upload some documents');
      }

      const payload: BatchUploadResponse = await response.json();

      const result = new Map<string, BatchUploadResponse['createdDocuments']>();

      fileGroups.forEach(([key]) => {
        const filesSlice = fileGroupRanges.get(key);

        if (filesSlice === undefined) {
          throw new Error('File key range mapping is missing');
        }

        const [start, end] = filesSlice;

        result.set(key, payload.createdDocuments.slice(start, end + 1));
      });

      return result;
    },
  });
}

export {useUploadDocuments};
export type {SuccessDocument};
