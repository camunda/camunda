/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {License} from 'modules/types';

function useLicense() {
  return useQuery<License, RequestError | Error>({
    queryKey: ['license'],
    queryFn: async () => {
      const {response, error} = await request(api.getLicense());

      if (response !== null) {
        const license = await response.json();
        return license;
      }

      throw error ?? new Error('Could not fetch license');
    },
  });
}

export {useLicense};
