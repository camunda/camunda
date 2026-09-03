/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modalV2";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { groupMutations } from "src/utility/api/groups/mutations";
import { RoleMultiSelect } from "src/components/formV2/entitySelection/RoleSelection";
import FormModal from "src/components/modalV2/FormModal";
import { useNotifications } from "src/components/notifications";
import type { Group, Role } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignRolesModal: FC<
  UseEntityModalCustomProps<{ id: Group["groupId"] }, { assignedRoles: Role[] }>
> = ({ entity: group, assignedRoles, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [loadingAssignRole, setLoadingAssignRole] = useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignRole } = useMutation(
    groupMutations.assignRole(qc),
  );

  const canSubmit = group && selectedRoles.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignRole(true);
    try {
      await Promise.all(
        selectedRoles.map(({ roleId }) =>
          callAssignRole({ roleId, groupId: group.id }),
        ),
      );
      if (selectedRoles.length === 1) {
        enqueueNotification({
          kind: "success",
          title: t("roleAssigned"),
          subtitle: t("roleAssignedSuccessfully"),
        });
      } else {
        enqueueNotification({
          kind: "success",
          title: t("rolesAssigned"),
          subtitle: t("rolesAssignedSuccessfully"),
        });
      }
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignRole(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedRoles([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignRole")}
      confirmLabel={t("assignRole")}
      loading={loadingAssignRole}
      loadingDescription={t("assigningRole")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
    >
      <p>
        <Translate i18nKey="searchAndAssignRoleToGroup">
          Search and assign role to group
        </Translate>
      </p>
      <RoleMultiSelect
        value={selectedRoles}
        onChange={setSelectedRoles}
        excluded={assignedRoles}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignRolesModal;
