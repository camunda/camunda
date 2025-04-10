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
import { unassignGroupMapping } from "src/utility/api/groups";

type RemoveGroupMappingModalProps = UseEntityModalCustomProps<
  Mapping,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupMappingModalProps> = ({
  entity: mapping,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMapping, { loading }] = useApiCall(unassignGroupMapping);

  const handleSubmit = async () => {
    if (groupId && mapping) {
      const { success } = await callUnassignMapping({
        groupId,
        mappingId: mapping.mappingId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupMappingRemoved"),
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
          i18nKey="removeMappingConfirmation"
          values={{ mappingId: mapping.mappingId }}
        >
          Are you sure you want to remove <strong>{mapping.mappingId}</strong>{" "}
          from this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
