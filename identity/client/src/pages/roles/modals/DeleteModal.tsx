/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useApiCall } from "src/utility/api/hooks";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { deleteRole, DeleteRoleParams } from "src/utility/api/roles";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<DeleteRoleParams>> = ({
  open,
  onClose,
  onSuccess,
  entity: { roleKey, name },
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteRole);

  const handleSubmit = async () => {
    const { success } = await apiCall({ roleKey });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("roleHasBeenDeleted"),
        subtitle: t("deleteRoleSuccess", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteRole")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingRole")}
      onClose={onClose}
      confirmLabel={t("deleteRole")}
    >
      <p>
        <Translate
          i18nKey="deleteRoleConfirmation"
          values={{ roleKey: roleKey }}
        >
          Are you sure you want to delete <strong>{roleKey}</strong>?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
