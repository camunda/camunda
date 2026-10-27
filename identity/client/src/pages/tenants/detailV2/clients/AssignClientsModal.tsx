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
import { tenantMutations } from "src/utility/api/tenants/mutations";
import FormModal from "src/components/modalV2/FormModal";
import TextField from "src/components/formV2/TextField";
import { UseEntityModalProps } from "src/components/modalV2";
import type { Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignClientsModal: FC<UseEntityModalProps<Tenant["tenantId"]>> = ({
  entity: tenantId,
  onSuccess,
  open,
  onClose,
}) => {
  const { t } = useTranslate("tenants");
  const [clientId, setClientId] = useState<string>("");
  const qc = useQueryClient();
  const { mutate, isPending: loadingAssignClient } = useMutation(
    tenantMutations.assignClient(qc),
  );

  const canSubmit = tenantId && clientId.length;

  const handleSubmit = () => {
    if (!canSubmit) return;
    mutate({ clientId, tenantId }, { onSuccess });
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
    >
      <p>{t("assignClientToTenant")}</p>
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
