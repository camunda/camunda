/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { assignTenantGroup, Tenant } from "src/utility/api/tenants";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import { Group } from "src/utility/api/groups";
import FormModal from "src/components/modal/FormModal";
import TextField from "src/components/form/TextField";

const AssignGroupModal: FC<
  UseEntityModalCustomProps<
    { tenantId: Tenant["tenantId"] },
    { assignedGroups: Group[] }
  >
> = ({ entity: { tenantId }, onSuccess, open, onClose }) => {
  const { t } = useTranslate("tenants");
  const [groupId, setGroupId] = useState("");
  const [loadingAssignGroup, setLoadingAssignGroup] = useState(false);

  const [callAssignGroup] = useApiCall(assignTenantGroup);

  const canSubmit = tenantId && groupId;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignGroup(true);
    const { success } = await callAssignGroup({ groupId, tenantId });
    setLoadingAssignGroup(false);

    if (success) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setGroupId("");
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
      <p>{t("assignGroupsToTenant")}</p>
      <TextField
        label={t("groupId")}
        placeholder={t("typeGroup")}
        onChange={setGroupId}
        value={groupId}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignGroupModal;
