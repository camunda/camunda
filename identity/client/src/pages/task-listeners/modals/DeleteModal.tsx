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
  deleteTaskListener,
  TaskListener,
} from "src/utility/api/task-listeners";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<TaskListener>> = ({
  open,
  onClose,
  onSuccess,
  entity: { id, type },
}) => {
  const { t } = useTranslate("taskListeners");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteTaskListener);

  const handleSubmit = async () => {
    const { success } = await apiCall({ id });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("taskListenerDeleted"),
        subtitle: type,
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteTaskListener")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingTaskListener")}
      onClose={onClose}
      confirmLabel={t("delete")}
    >
      <p>{t("deleteTaskListenerConfirmation")}</p>
    </Modal>
  );
};

export default DeleteModal;
