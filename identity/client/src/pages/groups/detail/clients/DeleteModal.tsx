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
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { Client, Group } from "src/utility/api/groups";
import { unassignGroupClient } from "src/utility/api/groups";

type RemoveGroupClientModalProps = UseEntityModalCustomProps<
  Client,
  {
    groupId: Group["groupId"];
  }
>;

const DeleteModal: FC<RemoveGroupClientModalProps> = ({
  entity: client,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignClient, { loading }] = useApiCall(unassignGroupClient);

  const handleSubmit = async () => {
    if (groupId && client) {
      const { success } = await callUnassignClient({
        groupId,
        clientId: client.clientId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupClientRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeClient")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingClient")}
      onClose={onClose}
      confirmLabel={t("removeClient")}
    >
      <p>
        <Translate
          i18nKey="removeClientFromGroup"
          values={{ clientId: client.clientId }}
        >
          Are you sure you want to remove <strong>{client.clientId}</strong>{" "}
          from this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
