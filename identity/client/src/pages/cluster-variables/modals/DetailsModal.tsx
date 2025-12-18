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
import { beautify } from "src/utility/components/editor/jsonUtils.ts";
import JSONEditor from "src/components/form/JSONEditor.tsx";

const DetailsModal: FC<
  UseEntityModalProps<{ name: string; value: string }>
> = ({ open, onClose, entity: clusterVariable }) => {
  const { t } = useTranslate("clusterVariables");

  return (
    <PassiveModal
      preventCloseOnClickOutside
      open={open}
      onClose={onClose}
      headline={clusterVariable.name}
      size="md"
    >
      <JSONEditor
        readOnly
        label={t("clusterVariableDetailsValue")}
        value={beautify(clusterVariable.value)}
        copy
        copyProps={{
          notificationText: t("copiedClusterVariableValue", {
            name: clusterVariable.name,
          }),
        }}
      />
    </PassiveModal>
  );
};

export default DetailsModal;
