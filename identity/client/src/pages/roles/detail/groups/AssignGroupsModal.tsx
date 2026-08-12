/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { roleMutations } from "src/utility/api/roles/mutations";
import { GroupMultiSelect } from "src/components/form/entitySelection/GroupSelection";
import FormModal from "src/components/modal/FormModal";
import type { Group, Role } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignGroupsModal: FC<
  UseEntityModalCustomProps<
    { roleId: Role["roleId"] },
    { assignedGroups: Group[] }
  >
> = ({ entity: { roleId }, assignedGroups, onSuccess, open, onClose }) => {
  const { t } = useTranslate("roles");
  const [selectedGroups, setSelectedGroups] = useState<Group[]>([]);
  const [loadingAssignGroup, setLoadingAssignGroup] = useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignGroup } = useMutation(
    roleMutations.assignGroup(qc),
  );

  const canSubmit = roleId && selectedGroups.length && !loadingAssignGroup;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignGroup(true);
    try {
      await Promise.all(
        selectedGroups.map(({ groupId }) =>
          callAssignGroup({ groupId, roleId }),
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
      <p>{t("searchAndAssignGroupToRole")}</p>
      <GroupMultiSelect
        value={selectedGroups}
        onChange={setSelectedGroups}
        excluded={assignedGroups}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignGroupsModal;
