/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNotifications } from "src/components/notifications";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import { deleteClusterVariable } from "src/utility/api/cluster-variables";
import type { ClusterVariable } from "@camunda/camunda-api-zod-schemas/8.10";

const DeleteModal: FC<
  UseEntityModalProps<Pick<ClusterVariable, "scope" | "tenantId" | "name">>
> = ({ open, onClose, onSuccess, entity: deleteClusterVariableParams }) => {
  const { t, Translate } = useTranslate("clusterVariables");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteClusterVariable);

  const handleSubmit = async () => {
    const { success } = await apiCall(deleteClusterVariableParams);

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("clusterVariableHasBeenDeleted"),
        subtitle: t("deleteClusterVariableSuccess", {
          name: deleteClusterVariableParams.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteClusterVariable")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingClusterVariable")}
      onClose={onClose}
      confirmLabel={t("deleteClusterVariable")}
    >
      <p>
        <Translate
          i18nKey="deleteClusterVariableConfirmation"
          values={{ name: deleteClusterVariableParams.name }}
        />
      </p>
    </Modal>
  );
};

export default DeleteModal;
