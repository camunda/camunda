/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { UnorderedList, ListItem, Stack } from "@carbon/react";
import { spacing04 } from "@carbon/elements";
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
  entity: { mappingKey, name, claimName, claimValue },
}) => {
  const { t } = useTranslate("mappings");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteMapping);

  const handleSubmit = async () => {
    const { success } = await apiCall({ mappingKey });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("MappingsDeleted"),
        subtitle: t("deleteMappingsSuccess", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteMappings")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingMappings")}
      onClose={onClose}
      confirmLabel={t("deleteMappings")}
    >
      <Stack gap={spacing04}>
        {t("Are you sure you want to delete the following Mapping:")}
        <UnorderedList>
          <ListItem>
            <strong>{t("Key")}</strong>: {mappingKey}
          </ListItem>
          <ListItem>
            <strong>{t("Name")}</strong>: {name}
          </ListItem>
          <ListItem>
            <strong>{t("Claim name")}</strong>: {claimName}
          </ListItem>
          <ListItem>
            <strong>{t("Claim value")}</strong>: {claimValue}
          </ListItem>
        </UnorderedList>
      </Stack>
    </Modal>
  );
};

export default DeleteMappingsModal;
