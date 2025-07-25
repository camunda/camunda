/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import styled from "styled-components";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api";
import { searchGroups, Group } from "src/utility/api/groups";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignTenantGroup, Tenant } from "src/utility/api/tenants";

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

  const {
    data: groupSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchGroups);

  const [callAssignGroup] = useApiCall(assignTenantGroup);

  const unassignedGroups =
    groupSearchResults?.items.filter(
      ({ groupId }) =>
        !assignedGroups.some((group) => group.groupId === groupId) &&
        !selectedGroups.some((group) => group.groupId === groupId) &&
        groupId.toLowerCase().includes(currentInputValue.toLowerCase()),
    ) || [];

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

    const results = await Promise.all(
      selectedGroups.map(({ groupId }) =>
        callAssignGroup({ groupId, tenantId }),
      ),
    );

    setLoadingAssignGroup(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
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
        items={unassignedGroups}
        itemTitle={({ groupId }) => groupId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByGroupId")}
        onSelect={onSelectGroup}
        onChange={handleDropdownChange}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("groupsCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignGroupsModal;
