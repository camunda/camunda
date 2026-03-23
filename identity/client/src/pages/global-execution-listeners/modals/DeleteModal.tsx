/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { UseEntityModalProps } from "src/components/modal";
import Modal from "src/components/modal/Modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import {
  deleteGlobalExecutionListener,
  GlobalExecutionListener,
} from "src/utility/api/global-execution-listeners";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<GlobalExecutionListener>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("globalExecutionListeners");
  const { enqueueNotification } = useNotifications();
  const [callDeleteGlobalExecutionListener, { loading }] = useApiCall(
    deleteGlobalExecutionListener,
  );

  const handleDelete = async () => {
    if (!entity) return;

    const { success } = await callDeleteGlobalExecutionListener({
      id: entity.id,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("globalExecutionListenerDeleted"),
        subtitle: entity.type,
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteGlobalExecutionListener")}
      onClose={onClose}
      onSubmit={handleDelete}
      loading={loading}
      loadingDescription={t("deletingGlobalExecutionListener")}
      confirmLabel={t("delete")}
      danger
    >
      <p>{t("deleteGlobalExecutionListenerConfirmation")}</p>
    </Modal>
  );
};

export default DeleteModal;
