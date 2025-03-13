/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { deleteTenant, DeleteTenantParams } from "src/utility/api/tenants";
import { useNotifications } from "src/components/notifications";

const DeleteTenantModal: FC<UseEntityModalProps<DeleteTenantParams>> = ({
  open,
  onClose,
  onSuccess,
  entity: { tenantId, name },
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteTenant);

  const handleSubmit = async () => {
    const { success } = await apiCall({ tenantId });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("tenantDeleted"),
        subtitle: t("deleteTenantSuccess", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteTenant")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingTenant")}
      onClose={onClose}
      confirmLabel={t("deleteTenant")}
    >
      <p>
        <Translate
          i18nKey="deleteTenantConfirmation"
          values={{ tenantId: tenantId }}
        >
          Are you sure you want to delete <strong>{tenantId}</strong>? This
          action cannot be undone.
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteTenantModal;
