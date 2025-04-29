/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';

function useAssignTask() {
  return useMutation({
    mutationFn: async (params: Parameters<typeof api.assignTask>[0]) => {
      const {response, error} = await request(api.assignTask(params));

      if (response !== null) {
        return null;
      }

      throw error;
    },
  });
}

export {useAssignTask};
