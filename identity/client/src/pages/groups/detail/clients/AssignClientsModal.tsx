/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import { Client } from "src/utility/api/groups";
import FormModal from "src/components/modal/FormModal";
import { assignGroupClient, Group } from "src/utility/api/groups";
import TextField from "src/components/form/TextField";
import { UseEntityModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";

const AssignClientsModal: FC<
  UseEntityModalProps<{ groupId: Group["groupId"] }>
> = ({ entity: { groupId }, onSuccess, open, onClose }) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [clientId, setClientId] = useState<Client["clientId"]>("");
  const [loadingAssignClient, setLoadingAssignClient] = useState(false);

  const [callAssignClient] = useApiCall(assignGroupClient);

  const canSubmit = groupId && clientId.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignClient(true);
    const { success } = await callAssignClient({
      clientId,
      groupId,
    });
    setLoadingAssignClient(false);

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("clientAssigned"),
        subtitle: t("clientAssignedSuccessfully"),
      });
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setClientId("");
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignClient")}
      confirmLabel={t("assignClient")}
      loading={loadingAssignClient}
      loadingDescription={t("assigningClient")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>{t("assignClientToGroup")}</p>
      <TextField
        label={t("clientId")}
        placeholder={t("enterClientId")}
        onChange={setClientId}
        value={clientId}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignClientsModal;
