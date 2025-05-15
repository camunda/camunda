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
import { Client, Tenant } from "src/utility/api/tenants";
import { unassignTenantClient } from "src/utility/api/tenants";

type RemoveTenantClientModalProps = UseEntityModalCustomProps<
  Client,
  {
    tenantId: Tenant["tenantId"];
  }
>;

const DeleteModal: FC<RemoveTenantClientModalProps> = ({
  entity: client,
  open,
  onClose,
  onSuccess,
  tenantId,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const [callUnassignClient, { loading }] = useApiCall(unassignTenantClient);

  const handleSubmit = async () => {
    if (tenantId && client) {
      const { success } = await callUnassignClient({
        tenantId,
        clientId: client.clientId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("tenantClientRemoved"),
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
          i18nKey="removeClientFromTenant"
          values={{ clientId: client.clientId }}
        >
          Are you sure you want to remove <strong>{client.clientId}</strong>{" "}
          from this tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
