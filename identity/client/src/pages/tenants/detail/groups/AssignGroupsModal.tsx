/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useState } from "react";
import styled from "styled-components";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { groupQueries } from "src/utility/api/groups/queries";
import { tenantMutations } from "src/utility/api/tenants/mutations";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import type { Group, Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

const SelectedGroups = styled.div`
  margin-top: 0;
`;

const AssignGroupsModal: FC<
  UseEntityModalCustomProps<
    { tenantId: Tenant["tenantId"] },
    { assignedGroups: Group[] }
  >
> = ({ entity: { tenantId }, assignedGroups, onSuccess, open, onClose }) => {
  const { t } = useTranslate("tenants");
  const [selectedGroups, setSelectedGroups] = useState<Group[]>([]);
  const [loadingAssignGroup, setLoadingAssignGroup] = useState(false);
  const [currentInputValue, setCurrentInputValue] = useState("");

  const [search, setSearch] = useState<Record<string, unknown>>({});
  useEffect(() => {
    if (currentInputValue === "") {
      setSearch({});
      return;
    }
    setSearch({ filter: { groupId: { $like: `*${currentInputValue}*` } } });
  }, [currentInputValue]);

  const {
    data: groupSearchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useQuery(groupQueries.search(search));

  const qc = useQueryClient();
  const { mutateAsync: callAssignGroup } = useMutation(
    tenantMutations.assignGroup(qc),
  );

  const unassignedFilter = useCallback(
    ({ groupId }: Group) =>
      !assignedGroups.some((group) => group.groupId === groupId) &&
      !selectedGroups.some((group) => group.groupId === groupId),
    [assignedGroups, selectedGroups],
  );

  const handleDropdownChange = (value: string) => {
    setCurrentInputValue(value);
  };

  const onSelectGroup = (group: Group) => {
    setSelectedGroups([...selectedGroups, group]);
    setCurrentInputValue("");
  };

  const onUnselectGroup =
    ({ groupId }: Group) =>
    () => {
      setSelectedGroups(
        selectedGroups.filter((group) => group.groupId !== groupId),
      );
    };

  const canSubmit = tenantId && selectedGroups.length && !loadingAssignGroup;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignGroup(true);
    try {
      await Promise.all(
        selectedGroups.map(({ groupId }) =>
          callAssignGroup({ groupId, tenantId }),
        ),
      );
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignGroup(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedGroups([]);
      setCurrentInputValue("");
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignGroup")}
      confirmLabel={t("assignGroup")}
      loading={loadingAssignGroup}
      loadingDescription={t("assigningGroup")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>{t("searchAndAssignGroupToTenant")}</p>
      {selectedGroups.length > 0 && (
        <SelectedGroups>
          {selectedGroups.map((group) => (
            <Tag
              key={group.groupId}
              onClose={onUnselectGroup(group)}
              size="md"
              type="blue"
              filter
            >
              {group.groupId}
            </Tag>
          ))}
        </SelectedGroups>
      )}
      <DropdownSearch
        autoFocus
        items={groupSearchResults?.items || []}
        itemTitle={({ groupId }) => groupId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByGroupId")}
        onSelect={onSelectGroup}
        onChange={handleDropdownChange}
        filter={unassignedFilter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("groupsCouldNotLoad")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </FormModal>
  );
};

export default AssignGroupsModal;
