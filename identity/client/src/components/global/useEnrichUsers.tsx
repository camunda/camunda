/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useEffect, useState } from "react";
import { isOIDC } from "src/configuration";
import { SearchResponse } from "src/utility/api";
import { useApiCall, usePaginatedApiCall } from "src/utility/api";
import { MemberUser } from "src/utility/api/membership";
import { ApiDefinition } from "src/utility/api/request";
import { searchUser, User } from "src/utility/api/users";

type UseEnrichedUsersResult = {
  users: User[];
  loading: boolean;
  success: boolean;
  reload: () => void;
  paginationProps: ReturnType<typeof usePaginatedApiCall>[1];
};

export function useEnrichedUsers<P>(
  apiDefinition: ApiDefinition<SearchResponse<MemberUser>, P>,
  params: P,
): UseEnrichedUsersResult {
  const [callSearchMembers, paginationProps] =
    usePaginatedApiCall(apiDefinition);
  const [callSearchUser] = useApiCall(searchUser);

  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    setSuccess(false);

    try {
      const result = await callSearchMembers(params);
      const members = result.data?.items || [];

      if (members.length === 0) {
        setUsers([]);
        setSuccess(true);
        return;
      }

      if (isOIDC) {
        setUsers(
          members.map(({ username }) => ({
            username,
            name: "",
            email: "",
          })),
        );
        setSuccess(true);
        return;
      }

      const usernames = members.map(({ username }) => username);
      const userResult = await callSearchUser({ usernames });
      setUsers(userResult.data?.items || []);
      setSuccess(true);
    } catch {
      setUsers([]);
      setSuccess(false);
    } finally {
      setLoading(false);
    }
  }, [callSearchMembers, callSearchUser, JSON.stringify(params)]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  return {
    users,
    loading,
    success,
    reload: fetch,
    paginationProps,
  };
}
