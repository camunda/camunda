/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { FormLabel, Tag } from "@carbon/react";
import { useApi } from "src/utility/api";
import { searchUser } from "src/utility/api/users";
import useTranslate from "src/utility/localization";
import DropdownSearch from "src/components/form/DropdownSearch";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

type OwnerSelectionSearchProps = {
  onChange: (ownerId: string) => void;
  ownerId?: string;
  isEmpty?: boolean;
};

const OwnerSelectionSearch: FC<OwnerSelectionSearchProps> = ({
  onChange,
  ownerId,
  isEmpty = false,
}) => {
  const { t } = useTranslate("authorizations");
  const [hasSearchText, setHasSearchText] = useState(false);
  const USER_SEARCH_PAGE_LIMIT = 50;
  const [search, setSearch] = useState<Record<string, unknown>>({
    page: { limit: USER_SEARCH_PAGE_LIMIT },
  });
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  useEffect(() => {
    if (!ownerId) {
      setSelectedUser(null);
    }
  }, [ownerId]);

  const handleSearchChange = (searchText: string) => {
    if (searchText === "") {
      setSearch({ page: { limit: USER_SEARCH_PAGE_LIMIT } });
      setHasSearchText(false);
      return;
    }

    setHasSearchText(true);
    setSearch({
      filter: { username: { $like: `*${searchText}*` } },
      page: { limit: USER_SEARCH_PAGE_LIMIT },
    });
  };

  const { data: userSearchResults } = useApi(searchUser, search, {
    paramsValid: hasSearchText,
  });

  const handleSelect = (user: User) => {
    setSelectedUser(user);
    onChange(user.username);
  };

  const handleClear = () => {
    setSelectedUser(null);
    onChange("");
  };

  return (
    <div>
      <FormLabel>{t("owner")}</FormLabel>
      {selectedUser ? (
        <div style={{ marginTop: "0.5rem" }}>
          <Tag filter onClose={handleClear} type="blue" size="md">
            {selectedUser.name || selectedUser.username}
          </Tag>
        </div>
      ) : (
        <DropdownSearch
          items={userSearchResults?.items || []}
          keyAttribute="username"
          itemTitle={({ username }) => username}
          itemSubTitle={({ email }) => email}
          placeholder={t("searchByOwnerId")}
          onSelect={handleSelect}
          onChange={handleSearchChange}
          invalid={isEmpty}
        />
      )}
      {isEmpty && (
        <p
          style={{
            color: "var(--cds-text-error, #da1e28)",
            fontSize: "0.75rem",
            marginTop: "0.25rem",
          }}
        >
          {t("ownerRequired")}
        </p>
      )}
    </div>
  );
};

export default OwnerSelectionSearch;
