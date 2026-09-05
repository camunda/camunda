/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { Heading, Separator } from "@camunda/design-system";
import TextField from "src/components/formV2/TextField";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modalV2";
import { useNotifications } from "src/components/notifications";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { mappingRuleMutations } from "src/utility/api/mapping-rules/mutations";
import { isValidId, getIdPattern } from "src/utility/validate";

type FormData = {
  mappingRuleId: string;
  mappingRuleName: string;
  claimName: string;
  claimValue: string;
};

const AddMappingRuleModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(mappingRuleMutations.create(qc));

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

  const onSubmit = (data: FormData) => {
    mutate(
      {
        mappingRuleId: data.mappingRuleId,
        name: data.mappingRuleName,
        claimName: data.claimName,
        claimValue: data.claimValue,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("mappingRuleCreated"),
            subtitle: t("mappingRuleCreatedSuccessfully", {
              name: data.mappingRuleName,
            }),
          });
          onSuccess();
        },
      },
    );
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
          validate: (value) =>
            isValidId(value) ||
            t("pleaseEnterValidMappingRuleId", {
              pattern: getIdPattern(),
            }),
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
      <Separator />
      <div className="grid gap-2">
        <Heading as="h3" variant="heading-xs">
          {t("mappingRule")}
        </Heading>
        <div className="grid gap-2 grid-cols-[1fr_auto_1fr]">
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
          <div className="flex items-center justify-center self-center text-lg">
            =
          </div>
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
        </div>
      </div>
    </FormModal>
  );
};

export default AddMappingRuleModal;
