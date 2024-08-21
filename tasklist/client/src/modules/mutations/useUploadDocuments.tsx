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
  files: File[];
};

function useUploadDocuments() {
  return useMutation<null, RequestError | Error, Payload>({
    mutationFn: async ({files}) => {
      const responses = await Promise.all(
        files.map((file) => request(api.uploadDocuments({file}))),
      );

      if (responses.every(({response}) => response !== null)) {
        return null;
      }

      throw new Error('Failed to upload all files');
    },
  });
}

export {useUploadDocuments};
