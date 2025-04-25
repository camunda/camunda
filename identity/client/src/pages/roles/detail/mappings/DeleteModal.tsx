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
import { Mapping } from "src/utility/api/mappings";
import { unassignRoleMapping } from "src/utility/api/roles";

type RemoveRoleMappingModalProps = UseEntityModalCustomProps<
  Mapping,
  {
    roleId: string;
  }
>;

const DeleteModal: FC<RemoveRoleMappingModalProps> = ({
  entity: mapping,
  open,
  onClose,
  onSuccess,
  roleId,
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMapping, { loading }] = useApiCall(unassignRoleMapping);

  const handleSubmit = async () => {
    if (roleId && mapping) {
      const { success } = await callUnassignMapping({
        roleId,
        mappingId: mapping.mappingId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("roleMappingRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeMapping")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingMapping")}
      onClose={onClose}
      confirmLabel={t("removeMapping")}
    >
      <p>
        <Translate
          i18nKey="removeMappingFromRole"
          values={{ mappingId: mapping.mappingId }}
        >
          Are you sure you want to remove <strong>{mapping.mappingId}</strong>{" "}
          from this role?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
