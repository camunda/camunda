/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { Stack } from "@carbon/react";
import { spacing05 } from "@carbon/elements";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateMappingRule, MappingRule } from "src/utility/api/mapping-rules";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "./components";
import { useNotifications } from "src/components/notifications";

const EditModal: FC<UseEntityModalProps<MappingRule>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [callUpdateMappingRule, { loading, error }] = useApiCall(
    updateMappingRule,
    {
      suppressErrorNotification: true,
    },
  );
  type FormData = Pick<
    MappingRule,
    "mappingRuleId" | "name" | "claimName" | "claimValue"
  >;
  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      mappingRuleId: entity.mappingRuleId,
      name: entity.name,
      claimName: entity.claimName,
      claimValue: entity.claimValue,
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await callUpdateMappingRule({
      mappingRuleId: data.mappingRuleId,
      name: data.name,
      claimName: data.claimName,
      claimValue: data.claimValue,
    });
    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingRuleUpdated"),
        subtitle: t("mappingRuleUpdatedSuccessfully", {
          name: data.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("editMappingRule")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("updatingMappingRule")}
      confirmLabel={t("updateMappingRule")}
    >
      <Controller
        name="mappingRuleId"
        control={control}
        render={({ field }) => (
          <TextField {...field} label={t("mappingRuleId")} readOnly />
        )}
      />
      <Controller
        name="name"
        control={control}
        rules={{ required: t("mappingRuleNameRequired") }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("mappingRuleName")}
            placeholder={t("enterMappingRuleName")}
            helperText={t("uniqueNameForMappingRule")}
            errors={fieldState.error?.message}
            autoFocus
          />
        )}
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <Controller
              name="claimName"
              control={control}
              rules={{ required: t("claimNameRequired") }}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
                  label={t("claimName")}
                  placeholder={t("enterClaimName")}
                  helperText={t("customClaimName")}
                  errors={fieldState.error?.message}
                />
              )}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <Controller
              name="claimValue"
              control={control}
              rules={{ required: t("claimValueRequired") }}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
                  label={t("claimValue")}
                  placeholder={t("enterClaimValue")}
                  helperText={t("valueForClaim")}
                  errors={fieldState.error?.message}
                />
              )}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};

export default EditModal;
