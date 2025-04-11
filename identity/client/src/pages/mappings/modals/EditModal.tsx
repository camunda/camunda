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
  const { t } = useTranslate("mappingRules");
  const [callUpdateMapping, { loading, error }] = useApiCall(updateMapping, {
    suppressErrorNotification: true,
  });
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
      headline={t("editMapping")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      error={error}
      loadingDescription={t("updatingMapping")}
      confirmLabel={t("updateMapping")}
    >
      <TextField
        label={t("mappingId")}
        onChange={(mappingId) =>
          setMapping((mapping) => ({ ...mapping, mappingId }))
        }
        value={mapping.mappingId}
        readOnly
      />
      <TextField
        label={t("mappingName")}
        placeholder={t("enterMappingName")}
        onChange={(name) => setMapping((mapping) => ({ ...mapping, name }))}
        value={mapping.name}
        helperText={t("uniqueNameForMapping")}
        autoFocus
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <TextField
              label={t("claimName")}
              placeholder={t("enterClaimName")}
              onChange={(claimName) =>
                setMapping((mapping) => ({ ...mapping, claimName }))
              }
              value={mapping.claimName}
              helperText={t("customClaimName")}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <TextField
              label={t("claimValue")}
              placeholder={t("enterClaimValue")}
              onChange={(claimValue) =>
                setMapping((mapping) => ({ ...mapping, claimValue }))
              }
              value={mapping.claimValue}
              helperText={t("valueForClaim")}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};

export default EditModal;
