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
import { useUnassignTenantRole } from "src/utility/api/tenants/hooks";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

type RemoveTenantRoleModalProps = UseEntityModalCustomProps<
  Role,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantRoleModalProps> = ({
  entity: { roleId },
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const { mutate, isPending: loading } = useUnassignTenantRole();

  const handleSubmit = () => {
    if (tenant && roleId) {
      mutate(
        { tenantId: tenant, roleId },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("tenantRoleRemoved"),
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
      headline={t("removeRole")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingRole")}
      onClose={onClose}
      confirmLabel={t("removeRole")}
    >
      <p>
        <Translate i18nKey="removeRoleFromTenant" values={{ roleId }}>
          Are you sure you want to remove <strong>{roleId}</strong> from this
          tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
