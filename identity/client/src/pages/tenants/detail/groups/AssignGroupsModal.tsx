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
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { tenantMutations } from "src/utility/api/tenants/mutations";
import GroupSearchDropdown from "src/components/form/GroupSearchDropdown";
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

  const onSelectGroup = (group: Group) => {
    setSelectedGroups([...selectedGroups, group]);
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
      <GroupSearchDropdown
        autoFocus
        placeholder={t("searchByGroupId")}
        onSelect={onSelectGroup}
        filter={unassignedFilter}
        errorTitle={t("groupsCouldNotLoad")}
        retryLabel={t("retry")}
      />
    </FormModal>
  );
};

export default AssignGroupsModal;
