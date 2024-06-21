/*
 * @license Identity
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license
 * agreements. Licensed under a proprietary license. See the License.txt file for more information. You may not use this
 * file except in compliance with the proprietary license.
 */

import { FC } from "react";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useNotifications } from "src/components/notifications";
import { deleteRole, Role } from "src/utility/api/roles";

const DeleteModal: FC<UseEntityModalProps<Role>> = ({
  entity: role,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [callDeleteApi, { loading }] = useApiCall(deleteRole);

  const handleSubmit = async () => {
    if (role) {
      const { success } = await callDeleteApi({
        id: role.id,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("Role has been deleted."),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t('Are you sure you want to delete the role "{{ name }}"?', {
        name: role?.name,
      })}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Deleting role")}
      onClose={onClose}
    />
  );
};

export default DeleteModal;
