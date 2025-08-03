/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { createMappingRule } from "src/utility/api/mapping-rules";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "../components";
import { spacing05 } from "@carbon/elements";
import { Stack } from "@carbon/react";

type FormData = {
  mappingRuleId: string;
  mappingRuleName: string;
  claimName: string;
  claimValue: string;
};

export const AddMappingRuleModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall(createMappingRule, {
    suppressErrorNotification: true,
  });

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    defaultValues: {
      mappingRuleId: "",
      mappingRuleName: "",
      claimName: "",
      claimValue: "",
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await apiCall({
      mappingRuleId: data.mappingRuleId,
      name: data.mappingRuleName,
      claimName: data.claimName,
      claimValue: data.claimValue,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingRuleCreated"),
        subtitle: t("mappingRuleCreatedSuccessfully", {
          name: data.mappingRuleName,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("createNewMappingRule")}
      open={open}
      onClose={onClose}
      loading={loading}
      error={error}
      confirmLabel={t("createMappingRule")}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="mappingRuleId"
        control={control}
        rules={{
          required: t("mappingRuleIdRequired"),
        }}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("mappingRuleId")}
            placeholder={t("enterMappingRuleId")}
            helperText={t("uniqueIdForMappingRule")}
            errors={errors.mappingRuleId?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="mappingRuleName"
        control={control}
        rules={{
          required: t("mappingRuleNameRequired"),
        }}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("mappingRuleName")}
            placeholder={t("enterMappingRuleName")}
            helperText={t("uniqueNameForMappingRule")}
            errors={errors.mappingRuleName?.message}
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
              rules={{
                required: t("claimNameRequired"),
              }}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t("claimName")}
                  placeholder={t("enterClaimName")}
                  helperText={t("customClaimName")}
                  errors={errors.claimName?.message}
                />
              )}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <Controller
              name="claimValue"
              control={control}
              rules={{
                required: t("claimValueRequired"),
              }}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t("claimValue")}
                  placeholder={t("enterClaimValue")}
                  helperText={t("valueForClaim")}
                  errors={errors.claimValue?.message}
                />
              )}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};
