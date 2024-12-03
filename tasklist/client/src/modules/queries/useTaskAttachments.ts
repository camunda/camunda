/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request} from 'modules/request';

type Attachment = {
  id: string;
  fileName: string;
  contentType: string;
};

function useTaskAttachments(taskId: string) {
  return useQuery<Attachment[]>({
    queryKey: ['taskAttachments', taskId],
    queryFn: async () => {
      const {response, error} = await request(
        api.v2.getTaskAttachments(taskId),
      );

      if (response !== null) {
        const attachments = await response.json();
        return attachments;
      }

      throw error;
    },
  });
}

export {useTaskAttachments};
