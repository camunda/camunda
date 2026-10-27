/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { groupMutations } from "src/utility/api/groups/mutations";
import FormModal from "src/components/modal/FormModal";
import TextField from "src/components/form/TextField";
import { UseEntityModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import type {
  Group,
  TenantClient,
} from "@camunda/camunda-api-zod-schemas/8.10";

const AssignClientsModal: FC<
  UseEntityModalProps<{ groupId: Group["groupId"] }>
> = ({ entity: { groupId }, onSuccess, open, onClose }) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [clientId, setClientId] = useState<TenantClient["clientId"]>("");
  const qc = useQueryClient();
  const { mutate, isPending: loadingAssignClient } = useMutation(
    groupMutations.assignClient(qc),
  );

  const canSubmit = groupId && clientId.length;

  const handleSubmit = () => {
    if (!canSubmit) return;
    mutate(
      { clientId, groupId },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("clientAssigned"),
            subtitle: t("clientAssignedSuccessfully"),
          });
          onSuccess();
        },
      },
    );
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
