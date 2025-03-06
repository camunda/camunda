/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api/hooks";
import { searchGroups, Group } from "src/utility/api/groups";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignTenantGroup, Tenant } from "src/utility/api/tenants";

const SelectedGroups = styled.div`
  margin-top: 0;
`;

const AssignGroupsModal: FC<
  UseEntityModalCustomProps<
    { id: Tenant["tenantKey"] },
    { assignedGroups: Group[] }
  >
> = ({ entity: tenant, assignedGroups, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedGroups, setSelectedGroups] = useState<Group[]>([]);
  const [loadingAssignGroup, setLoadingAssignGroup] = useState(false);

  const {
    data: groupSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchGroups);

  const [callAssignGroup] = useApiCall(assignTenantGroup);

  const unassignedGroups =
    groupSearchResults?.items.filter(
      ({ groupKey }) =>
        !assignedGroups.some((group) => group.groupKey === groupKey) &&
        !selectedGroups.some((group) => group.groupKey === groupKey),
    ) || [];

  const onSelectGroup = (group: Group) => {
    setSelectedGroups([...selectedGroups, group]);
  };

  const onUnselectGroup =
    ({ groupKey }: Group) =>
    () => {
      setSelectedGroups(
        selectedGroups.filter((group) => group.groupKey !== groupKey),
      );
    };

  const canSubmit = tenant && selectedGroups.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignGroup(true);

    const results = await Promise.all(
      selectedGroups.map(({ groupKey }) =>
        callAssignGroup({ groupKey, tenantId: tenant.id }),
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
      <p>
        <Translate>Search and assign group to tenant</Translate>
      </p>
      {selectedGroups.length > 0 && (
        <SelectedGroups>
          {selectedGroups.map((group) => (
            <Tag
              key={group.groupKey}
              onClose={onUnselectGroup(group)}
              size="md"
              type="blue"
              filter
            >
              {group.groupKey}
            </Tag>
          ))}
        </SelectedGroups>
      )}

      <DropdownSearch
        autoFocus
        items={unassignedGroups}
        itemTitle={({ groupKey }) => groupKey}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByGroupId")}
        onChange={() => null}
        onSelect={onSelectGroup}
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
