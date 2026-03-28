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
import { deleteGlobalExecutionListener } from "src/utility/api/global-execution-listeners";
import { useNotifications } from "src/components/notifications";
import type { GlobalExecutionListener } from "@camunda/camunda-api-zod-schemas/8.9";

const DeleteModal: FC<UseEntityModalProps<GlobalExecutionListener>> = ({
  open,
  onClose,
  onSuccess,
  entity: { id, type },
}) => {
  const { t } = useTranslate("globalExecutionListeners");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteGlobalExecutionListener);

  const handleSubmit = async () => {
    const { success } = await apiCall({ id });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("globalExecutionListenerDeleted"),
        subtitle: type,
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteGlobalExecutionListener")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingGlobalExecutionListener")}
      onClose={onClose}
      confirmLabel={t("delete")}
    >
      <p>{t("deleteGlobalExecutionListenerConfirmation")}</p>
    </Modal>
  );
};

export default DeleteModal;
