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
import { useNotifications } from "src/components/notifications";
import {deleteAuthorization, DeleteAuthorizationParams} from "src/utility/api/authorizations";

const DeleteAuthorizationModal: FC<UseEntityModalProps<DeleteAuthorizationParams>> = ({
  open,
  onClose,
  onSuccess,
  entity: { key },
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteAuthorization);

  const handleSubmit = async () => {
    const { success } = await apiCall({ authKey: key });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Authorization deleted"),
        subtitle: t("You have successfully deleted authorization {{ key }}", {
          key,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t('Are you sure you want to delete the authorization "{{ key }}"?', {
        key,
      })}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Deleting Authorization")}
      onClose={onClose}
    />
  );
};

export default DeleteAuthorizationModal;
