/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Stack, UnorderedList, ListItem } from "@carbon/react";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { useDeleteAuthorization } from "src/utility/api/authorizations/hooks";
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
  const { mutate, isPending: loading } = useDeleteAuthorization();

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
      <Stack gap="4">
        {t("deleteConfirmation")}
        <UnorderedList>
          <ListItem>
            <strong>{t("ownerId")}</strong>: {ownerId}
          </ListItem>
          <ListItem>
            <strong>{t("ownerType")}</strong>: {ownerType}
          </ListItem>
          {resourceData.resourceType === "USER_TASK" ? (
            <ListItem>
              <strong>{t("resourcePropertyName")}</strong>:{" "}
              {resourceData.resourcePropertyName}
            </ListItem>
          ) : (
            <ListItem>
              <strong>{t("resourceId")}</strong>: {resourceData.resourceId}
            </ListItem>
          )}
          <ListItem>
            <strong>{t("permission")}</strong>: {permissionTypes.join(", ")}
          </ListItem>
        </UnorderedList>
        {t("irreversibleAction")}
      </Stack>
    </Modal>
  );
};

export default DeleteAuthorizationModal;
