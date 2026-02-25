/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import {
  deleteGlobalTaskListener,
  GlobalTaskListener,
} from "src/utility/api/global-task-listeners";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<GlobalTaskListener>> = ({
  open,
  onClose,
  onSuccess,
  entity: { id, type },
}) => {
  const { t } = useTranslate("globalTaskListeners");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteGlobalTaskListener);

  const handleSubmit = async () => {
    const { success } = await apiCall({ id });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("globalTaskListenerDeleted"),
        subtitle: type,
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteGlobalTaskListener")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingGlobalTaskListener")}
      onClose={onClose}
      confirmLabel={t("delete")}
    >
      <p>{t("deleteGlobalTaskListenerConfirmation")}</p>
    </Modal>
  );
};

export default DeleteModal;
