/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';

import debouncePromise from 'debouncePromise';

import {Identity, searchIdentities} from './service';

const debounceRequest = debouncePromise();

export default function useLoadIdentities({
  excludeGroups,
  fetchUsers,
}: {
  fetchUsers?: (
    query: string,
    excludeGroups?: boolean
  ) => Promise<{total: number; result: Identity[]}>;
  excludeGroups: boolean;
}) {
  const [loading, setLoading] = useState(true);
  const [identities, setIdentities] = useState<Identity[]>([]);
  const loadNewValues = useCallback(
    async (query: string, delay = 0) => {
      setLoading(true);

      const {result} = await debounceRequest(async () => {
        return await (fetchUsers || searchIdentities)(query, excludeGroups);
      }, delay);

      setIdentities(result);
      setLoading(false);
    },
    [fetchUsers, excludeGroups]
  );

  return {loading, setLoading, identities, loadNewValues};
}
