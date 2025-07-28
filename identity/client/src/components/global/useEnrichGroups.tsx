/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useEffect, useState } from "react";
import { isCamundaGroupsEnabled } from "src/configuration";
import { SearchResponse } from "src/utility/api";
import { useApiCall, usePaginatedApiCall } from "src/utility/api";
import { MemberGroup } from "src/utility/api/groups";
import { ApiDefinition } from "src/utility/api/request";
import { searchGroups, Group } from "src/utility/api/groups";

type UseEnrichedGroupsResult = {
  groups: Group[];
  loading: boolean;
  success: boolean;
  reload: () => void;
  paginationProps: ReturnType<typeof usePaginatedApiCall>[1];
};

export function useEnrichedGroups<P>(
  apiDefinition: ApiDefinition<SearchResponse<MemberGroup>, P>,
  params: P,
): UseEnrichedGroupsResult {
  const [callSearchMembers, paginationProps] =
    usePaginatedApiCall(apiDefinition);
  const [callSearchGroups] = useApiCall(searchGroups);

  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    setSuccess(false);

    try {
      const result = await callSearchMembers(params);
      const members = result.data?.items || [];

      if (members.length === 0) {
        setGroups([]);
        setSuccess(true);
        return;
      }

      if (!isCamundaGroupsEnabled) {
        setGroups(
          members.map(({ groupId }) => ({
            groupId,
            name: "",
            description: "",
          })),
        );
        setSuccess(true);
        return;
      }

      const groupIds = members.map(({ groupId }) => groupId);
      const groupResult = await callSearchGroups({ groupIds });
      setGroups(groupResult.data?.items || []);
      setSuccess(true);
    } catch {
      setGroups([]);
      setSuccess(false);
    } finally {
      setLoading(false);
    }
  }, [callSearchMembers, callSearchGroups, JSON.stringify(params)]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  return {
    groups,
    loading,
    success,
    reload: fetch,
    paginationProps,
  };
}
