/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { membershipMutations } from "src/utility/api/membership/mutations";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

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
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const qc = useQueryClient();
  const { mutate, isPending: loading } = useMutation(
    membershipMutations.unassignTenantMember(qc),
  );

  const handleSubmit = () => {
    if (tenant && user) {
      mutate(
        { tenantId: tenant, username: user.username },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("tenantMemberRemoved"),
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
      headline={t("removeUser")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingUser")}
      onClose={onClose}
      confirmLabel={t("removeUser")}
    >
      <p>
        <Translate
          i18nKey="deleteUserConfirmation"
          values={{ username: user.username }}
        >
          Are you sure you want to remove <strong>{user.username}</strong> from
          this tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
