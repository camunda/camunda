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
import FormModal from "src/components/modal/FormModal";
import { roleMutations } from "src/utility/api/roles/mutations";
import TextField from "src/components/form/TextField";
import { UseEntityModalProps } from "src/components/modal";
import type { Role, TenantClient } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignClientsModal: FC<UseEntityModalProps<Role["roleId"]>> = ({
  entity: roleId,
  onSuccess,
  open,
  onClose,
}) => {
  const { t, Translate } = useTranslate("roles");
  const [clientId, setClientId] = useState<TenantClient["clientId"]>("");
  const qc = useQueryClient();
  const { mutate, isPending: loadingAssignClient } = useMutation(
    roleMutations.assignClient(qc),
  );

  const canSubmit = roleId && clientId.length;

  const handleSubmit = () => {
    if (!canSubmit) return;
    mutate({ clientId, roleId }, { onSuccess });
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
      <p>
        <Translate i18nKey="assignClientToRole">
          Assign client to this role
        </Translate>
      </p>

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
