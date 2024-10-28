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
  documentType: string;
  storeId: string;
  documentId: string;
  metadata: {
    contentType: string;
    fileName: string;
    size: string;
  };
};

function useUploadDocuments() {
  return useMutation<
    Map<string, FileUploadMetadata[]>,
    RequestError | Error,
    Payload
  >({
    mutationFn: async ({files}) => {
      const requests: ReturnType<typeof request>[] = [];
      const fileRequestMapping = Array.from(files.entries()).map<
        [string, Promise<Awaited<ReturnType<typeof request>>[]>]
      >(([key, files]) => {
        const itemRequests = files.map((file) =>
          request(api.v2.uploadDocuments({file})),
        );

        requests.push(...itemRequests);

        return [key, Promise.all(itemRequests)];
      });
      const responses = await Promise.all(requests);

      if (responses.every(({response}) => response !== null && response.ok)) {
        const metadataMapping: [string, FileUploadMetadata[]][] = [];

        for (const [key, responses] of fileRequestMapping) {
          const metadata = await responses;

          metadataMapping.push([
            key,
            await Promise.all(metadata.map(({response}) => response!.json())),
          ]);
        }

        return new Map(metadataMapping);
      }

      throw new Error('Failed to upload all files');
    },
  });
}

export {useUploadDocuments};
export type {FileUploadMetadata};
