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
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.9';

function useUnassignTask() {
  return useMutation({
    mutationFn: async (userTaskKey: UserTask['userTaskKey']) => {
      const {response, error} = await request(api.unassignTask({userTaskKey}));

      if (response !== null) {
        return null;
      }

      throw error;
    },
  });
}

export {useUnassignTask};
