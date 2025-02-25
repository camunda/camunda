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
import { User } from "src/utility/api/users";
import { unassignTenantMember } from "src/utility/api/membership";

type RemoveTenantMemberModalProps = UseEntityModalCustomProps<
  User,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantMemberModalProps> = ({
  entity: user,
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [callUnassignMember, { loading }] = useApiCall(unassignTenantMember);

  const handleSubmit = async () => {
    if (tenant && user) {
      const { success } = await callUnassignMember({
        tenantId: tenant,
        userId: user.id!,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("Tenant member has been removed."),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("Remove user")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Removing user")}
      onClose={onClose}
      confirmLabel={t("Delete tenant")}
    >
      <p>
        <Translate>Are you sure you want to remove</Translate>{" "}
        <strong>{user.username}</strong> from this tenant?
      </p>
    </Modal>
  );
};

export default DeleteModal;
