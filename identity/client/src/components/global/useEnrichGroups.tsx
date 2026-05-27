/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiDefinition, unwrap } from "src/utility/api/request";
import { searchGroups } from "src/utility/api/groups";
import { usePagination } from "src/utility/api";
import { mergeParams } from "src/utility/api/hooks/utils";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import type {
  QueryGroupsResponseBody,
  QueryGroupsByRoleResponseBody,
  Group,
} from "@camunda/camunda-api-zod-schemas/8.10";

type UseEnrichedGroupsResult = {
  groups: Group[];
  loading: boolean;
  success: boolean;
  reload: () => void;
  paginationProps: {
    page: { pageNumber: number; pageSize: number; totalItems?: number };
    setPageNumber: (page: number) => void;
    setPageSize: (size: number) => void;
    setSort: ReturnType<typeof usePagination>["setSort"];
    setSearch: ReturnType<typeof usePagination>["setSearch"];
  };
};

export function useEnrichedGroups<P>(
  apiDefinition: ApiDefinition<
    QueryGroupsResponseBody | QueryGroupsByRoleResponseBody,
    P
  >,
  params: P,
  isCamundaGroupsEnabled: boolean,
): UseEnrichedGroupsResult {
  const { pageParams, page, setPageNumber, setPageSize, setSort, setSearch } =
    usePagination();

  const mergedParams = mergeParams(
    params as Record<string, unknown>,
    pageParams,
  ) as P;

  const membersQuery = useQuery({
    queryKey: ["enrichedGroups", apiDefinition.name, mergedParams],
    queryFn: () => unwrap(apiDefinition(mergedParams)(getApiBaseUrl())),
  });

  const members = membersQuery.data?.items ?? [];
  const groupIds = members.map(({ groupId }) => groupId);

  const groupsQuery = useQuery({
    queryKey: ["enrichedGroups", "groupDetails", groupIds],
    queryFn: () => unwrap(searchGroups({ groupIds })(getApiBaseUrl())),
    enabled: isCamundaGroupsEnabled && groupIds.length > 0,
  });

  const groups = useMemo<Group[]>(() => {
    const items = membersQuery.data?.items ?? [];
    if (items.length === 0) return [];
    if (!isCamundaGroupsEnabled) {
      return items.map(({ groupId }) => ({
        groupId,
        name: "",
        description: "",
      }));
    }
    const fullGroups = groupsQuery.data?.items ?? [];
    return items.map((member) => {
      const group = fullGroups.find((g) => g.groupId === member.groupId);
      return {
        groupId: member.groupId,
        name: group?.name || "",
        description: group?.description || "",
      };
    });
  }, [membersQuery.data, isCamundaGroupsEnabled, groupsQuery.data]);

  const loading =
    membersQuery.isLoading ||
    (isCamundaGroupsEnabled && groupIds.length > 0 && groupsQuery.isLoading);

  const success =
    membersQuery.isSuccess &&
    (!isCamundaGroupsEnabled || groupIds.length === 0 || groupsQuery.isSuccess);

  return {
    groups,
    loading,
    success,
    reload: () => {
      void membersQuery.refetch();
      if (isCamundaGroupsEnabled) void groupsQuery.refetch();
    },
    paginationProps: {
      page: { ...page, ...membersQuery.data?.page },
      setPageNumber,
      setPageSize,
      setSort,
      setSearch,
    },
  };
}
