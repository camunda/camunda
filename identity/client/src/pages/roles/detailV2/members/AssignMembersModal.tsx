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
import { membershipMutations } from "src/utility/api/membership/mutations";
import useTranslate from "src/utility/localization";
import { UserMultiSelect } from "src/components/formV2/entitySelection/UserSelection";
import FormModal from "src/components/modalV2/FormModal";
import type { Role, User } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignMembersModal: FC<
  UseEntityModalCustomProps<
    { roleId: Role["roleId"] },
    {
      assignedUsers: User[];
      onItemsAssigned: (count: number) => void;
    }
  >
> = ({
  entity: { roleId },
  assignedUsers,
  onSuccess,
  onItemsAssigned,
  open,
  onClose,
}) => {
  const { t } = useTranslate("roles");
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignUser } = useMutation(
    membershipMutations.assignRoleMember(qc),
  );

  const canSubmit = roleId && selectedUsers.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);
    try {
      await Promise.all(
        selectedUsers.map(({ username }) =>
          callAssignUser({ username, roleId }),
        ),
      );
      onItemsAssigned(selectedUsers.length);
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignUser(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedUsers([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignUser")}
      confirmLabel={t("assignUser")}
      loading={loadingAssignUser}
      loadingDescription={t("assigningUser")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
    >
      <p>{t("searchAndAssignUserToRole")}</p>
      <UserMultiSelect
        value={selectedUsers}
        onChange={setSelectedUsers}
        excluded={assignedUsers}
        placeholder={t("searchByUsernameOrName")}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMembersModal;
