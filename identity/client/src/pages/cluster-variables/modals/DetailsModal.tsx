/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import PassiveModal from "src/components/modal/PassiveModal.tsx";
import TextField from "src/components/form/TextField.tsx";
import { Button } from "@carbon/react";
import { Copy } from "@carbon/react/icons";
import { useNotifications } from "src/components/notifications";

const DetailsModal: FC<
  UseEntityModalProps<{ name: string; value: string }>
> = ({ open, onClose, entity: clusterVariable }) => {
  const { t } = useTranslate("clusterVariables");
  const { enqueueNotification } = useNotifications();

  const onCopy = async () => {
    await navigator.clipboard.writeText(clusterVariable.value);
    enqueueNotification({
      kind: "info",
      title: t("copiedClusterVariableValue", {
        name: clusterVariable.name,
      }),
    });
  };

  return (
    <PassiveModal
      preventCloseOnClickOutside
      open={open}
      onClose={onClose}
      headline={clusterVariable.name}
      size="md"
    >
      <TextField
        readOnly
        label={t("clusterVariableValue")}
        value={clusterVariable.value}
        cols={2}
        autoFocus
        decorator={
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Copy}
            tooltipPosition="bottom"
            iconDescription="Copy"
            onClick={onCopy}
          />
        }
      />
    </PassiveModal>
  );
};

export default DetailsModal;
