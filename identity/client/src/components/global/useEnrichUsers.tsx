/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type {
  User,
  QueryUsersResponseBody,
  QueryUsersByGroupResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, unwrap } from "src/utility/api/request";
import { searchUser } from "src/utility/api/users";
import { usePagination } from "src/utility/api";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { mergeParams } from "src/utility/api/hooks/utils";

type UseEnrichedUsersResult = {
  users: User[];
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

export function useEnrichedUsers<P>(
  apiDefinition: ApiDefinition<
    QueryUsersResponseBody | QueryUsersByGroupResponseBody,
    P
  >,
  params: P,
  isOIDC: boolean,
): UseEnrichedUsersResult {
  const { pageParams, page, setPageNumber, setPageSize, setSort, setSearch } =
    usePagination();

  const mergedParams = mergeParams(
    params as Record<string, unknown>,
    pageParams,
  ) as P;

  const membersQuery = useQuery({
    queryKey: ["enrichedMembers", apiDefinition.name, mergedParams],
    queryFn: () => unwrap(apiDefinition(mergedParams)(getApiBaseUrl())),
  });

  const memberItems = membersQuery.data?.items ?? [];
  const usernames = memberItems.map(({ username }) => username);

  const usersQuery = useQuery({
    queryKey: ["enrichedMembers", "userDetails", usernames],
    queryFn: () => unwrap(searchUser({ usernames })(getApiBaseUrl())),
    enabled: !isOIDC && usernames.length > 0,
  });

  const users = useMemo<User[]>(() => {
    const items = membersQuery.data?.items ?? [];
    if (items.length === 0) return [];
    if (isOIDC) {
      return items.map(({ username }) => ({
        username,
        name: "",
        email: "",
      }));
    }
    const fullUsers = usersQuery.data?.items ?? [];
    return items.map((member) => {
      const user = fullUsers.find((u) => u.username === member.username);
      return {
        username: member.username,
        name: user?.name || "",
        email: user?.email || "",
      };
    });
  }, [membersQuery.data, isOIDC, usersQuery.data]);

  const loading =
    membersQuery.isLoading ||
    (!isOIDC && usernames.length > 0 && usersQuery.isLoading);

  const success =
    membersQuery.isSuccess &&
    (isOIDC || usernames.length === 0 || usersQuery.isSuccess);

  return {
    users,
    loading,
    success,
    reload: () => {
      void membersQuery.refetch();
      if (!isOIDC) void usersQuery.refetch();
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
