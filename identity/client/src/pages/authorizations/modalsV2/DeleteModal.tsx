/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Text } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modalV2";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { authorizationMutations } from "src/utility/api/authorizations/mutations";
import { useNotifications } from "src/components/notifications";
import type { Authorization } from "@camunda/camunda-api-zod-schemas/8.10";

const DeleteAuthorizationModal: FC<UseEntityModalProps<Authorization>> = ({
  open,
  onClose,
  onSuccess,
  entity: {
    authorizationKey,
    ownerId,
    ownerType,
    permissionTypes,
    ...resourceData
  },
}) => {
  const { t } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const { mutate, isPending: loading } = useMutation(
    authorizationMutations.delete(qc),
  );

  const handleSubmit = () => {
    mutate(
      { authorizationKey },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("authorizationDeleted"),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <Modal
      open={open}
      headline={t("deleteAuthorization")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingAuthorization")}
      onClose={onClose}
      confirmLabel={t("deleteAuthorization")}
    >
      <Text>{t("deleteConfirmation")}</Text>
      <ul className="list-disc pl-5">
        <li>
          <strong>{t("ownerId")}</strong>: {ownerId}
        </li>
        <li>
          <strong>{t("ownerType")}</strong>: {ownerType}
        </li>
        {resourceData.resourceType === "USER_TASK" ? (
          <li>
            <strong>{t("resourcePropertyName")}</strong>:{" "}
            {resourceData.resourcePropertyName}
          </li>
        ) : (
          <li>
            <strong>{t("resourceId")}</strong>: {resourceData.resourceId}
          </li>
        )}
        <li>
          <strong>{t("permission")}</strong>: {permissionTypes.join(", ")}
        </li>
      </ul>
      <Text>{t("irreversibleAction")}</Text>
    </Modal>
  );
};

export default DeleteAuthorizationModal;
