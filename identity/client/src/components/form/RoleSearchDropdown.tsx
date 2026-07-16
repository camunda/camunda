/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { roleQueries } from "src/utility/api/roles/queries";
import DropdownSearch from "src/components/form/DropdownSearch";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

type RoleSearchDropdownProps = {
  onSelect: (role: Role) => void;
  filter?: (role: Role) => boolean;
  placeholder: string;
  autoFocus?: boolean;
  errorTitle: string;
  retryLabel: string;
};

const RoleSearchDropdown: FC<RoleSearchDropdownProps> = ({
  onSelect,
  filter,
  placeholder,
  autoFocus = false,
  errorTitle,
  retryLabel,
}) => {
  const [search, setSearch] = useState("");

  const {
    data: roleSearchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useQuery(
    roleQueries.search(search === "" ? {} : { filter: { name: search } }),
  );

  return (
    <>
      <DropdownSearch
        autoFocus={autoFocus}
        items={roleSearchResults?.items || []}
        itemTitle={({ roleId }) => roleId}
        itemSubTitle={({ name }) => name}
        placeholder={placeholder}
        onSelect={onSelect}
        onChange={setSearch}
        filter={filter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={errorTitle}
          actionButton={{
            label: retryLabel,
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </>
  );
};

export default RoleSearchDropdown;
