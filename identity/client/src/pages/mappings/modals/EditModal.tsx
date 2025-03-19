/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { Stack } from "@carbon/react";
import { spacing05 } from "@carbon/elements";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateMapping, Mapping } from "src/utility/api/mappings";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "./components";

const EditModal: FC<UseEntityModalProps<Mapping>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("mappings");
  const [callUpdateMapping, { loading }] = useApiCall(updateMapping);
  const [mapping, setMapping] = useState<Mapping>(entity);

  const handleSubmit = async () => {
    const { success } = await callUpdateMapping(mapping);
    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("Edit Mapping")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Updating mapping")}
      confirmLabel={t("Update Mapping")}
    >
      <TextField
        label={t("Mapping key")}
        onChange={(id) => setMapping((mapping) => ({ ...mapping, id }))}
        value={mapping.mappingKey}
        readOnly
      />
      <TextField
        label={t("Mapping name")}
        placeholder={t("Enter mapping name")}
        onChange={(name) => setMapping((mapping) => ({ ...mapping, name }))}
        value={mapping.name}
        helperText={t("Enter a unique name for this mapping")}
        autoFocus
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>Mapping Rule</h3>
          <CustomStack orientation="horizontal">
            <TextField
              label={t("Claim Name")}
              placeholder={t("Enter claim name")}
              onChange={(claimName) =>
                setMapping((mapping) => ({ ...mapping, claimName }))
              }
              value={mapping.claimName}
              helperText={t("Enter a custom claim name")}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <TextField
              label={t("Claim Value")}
              placeholder={t("Enter claim value")}
              onChange={(claimValue) =>
                setMapping((mapping) => ({ ...mapping, claimValue }))
              }
              value={mapping.claimValue}
              helperText={t("Enter the value for the claim")}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};

export default EditModal;
