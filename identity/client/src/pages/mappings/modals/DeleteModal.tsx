/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useApiCall } from "src/utility/api";
import { deleteMapping, DeleteMappingParams } from "src/utility/api/mappings";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";

const DeleteMappingsModal: FC<UseEntityModalProps<DeleteMappingParams>> = ({
  open,
  onClose,
  onSuccess,
  entity: { mappingRuleId, name },
}) => {
  const { t, Translate } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteMapping);

  const handleSubmit = async () => {
    const { success } = await apiCall({ mappingRuleId });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingDeleted"),
        subtitle: t("deleteMappingSuccess", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteMapping")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingMapping")}
      onClose={onClose}
      confirmLabel={t("deleteMapping")}
    >
      <p>
        <Translate
          i18nKey="deleteMappingConfirmation"
          values={{ mappingName: name || mappingRuleId }}
        >
          Are you sure you want to delete <strong>{name || mappingRuleId}</strong>?
          This action cannot be undone.
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteMappingsModal;
