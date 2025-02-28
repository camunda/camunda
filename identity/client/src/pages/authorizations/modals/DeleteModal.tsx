/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { Stack, UnorderedList, ListItem } from "@carbon/react";
import { spacing04 } from "@carbon/elements";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import {
  Authorization,
  deleteAuthorization,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";

const DeleteAuthorizationModal: FC<UseEntityModalProps<Authorization>> = ({
  open,
  onClose,
  onSuccess,
  entity: { authorizationKey, ownerId, ownerType, resourceId, permissionTypes },
}) => {
  const { t } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteAuthorization);

  const handleSubmit = async () => {
    const { success } = await apiCall({ authorizationKey });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("authorizationDeleted"),
      });
      onSuccess();
    }
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
      <Stack gap={spacing04}>
        {t("deleteConfirmation")}
        <UnorderedList>
          <ListItem>
            <strong>{t("ownerId")}</strong>: {ownerId}
          </ListItem>
          <ListItem>
            <strong>{t("ownerType")}</strong>: {ownerType}
          </ListItem>
          <ListItem>
            <strong>{t("resourceId")}</strong>: {resourceId}
          </ListItem>
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
