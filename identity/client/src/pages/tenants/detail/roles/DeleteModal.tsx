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
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { Role } from "src/utility/api/roles";
import { unassignTenantRole } from "src/utility/api/tenants";

type RemoveTenantRoleModalProps = UseEntityModalCustomProps<
  Role,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantRoleModalProps> = ({
  entity: role,
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const [callUnassignRole, { loading }] = useApiCall(unassignTenantRole);

  const handleSubmit = async () => {
    if (tenant && role) {
      const { success } = await callUnassignRole({
        tenantId: tenant,
        key: role.key,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("tenantRoleRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeRole")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingRole")}
      onClose={onClose}
      confirmLabel={t("removeRole")}
    >
      <p>
        <Translate
          i18nKey="removeRoleFromTenant"
          values={{ roleKey: role.key }}
        >
          Are you sure you want to remove <strong>{role.key}</strong> from this
          tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
