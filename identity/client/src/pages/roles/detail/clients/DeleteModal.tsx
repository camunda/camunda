/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { roleMutations } from "src/utility/api/roles/mutations";
import type { Role, TenantClient } from "@camunda/camunda-api-zod-schemas/8.10";

type RemoveRoleClientModalProps = UseEntityModalCustomProps<
  TenantClient,
  {
    roleId: Role["roleId"];
  }
>;

const DeleteModal: FC<RemoveRoleClientModalProps> = ({
  entity: client,
  open,
  onClose,
  onSuccess,
  roleId,
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();

  const qc = useQueryClient();
  const { mutate, isPending: loading } = useMutation(
    roleMutations.unassignClient(qc),
  );

  const handleSubmit = () => {
    if (roleId && client) {
      mutate(
        { roleId, clientId: client.clientId },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("roleClientRemoved"),
            });
            onSuccess();
          },
        },
      );
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
          i18nKey="removeClientFromRole"
          values={{ clientId: client.clientId }}
        >
          Are you sure you want to remove <strong>{client.clientId}</strong>{" "}
          from this role?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
