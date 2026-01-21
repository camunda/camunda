/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import {
  createClusterVariable,
  ScopeType,
} from "src/utility/api/cluster-variables";
import { RadioButton, RadioButtonGroup, Stack } from "@carbon/react";
import { Controller, useForm } from "react-hook-form";
import { useNotifications } from "src/components/notifications";
import { FormModal, UseModalProps } from "src/components/modal";
import { spacing06 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField.tsx";
import JSONEditor from "src/components/form/JSONEditor.tsx";
import { isValid } from "src/utility/components/editor/jsonUtils.ts";
import { isSaaS } from "src/configuration";
import ClusterVariableTenantDropdown from "./ClusterVariableTenantDropdown.tsx";

type FormData = {
  name: string;
  value: string;
  scope: ScopeType;
  tenantId?: string;
};

export const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("clusterVariables");
  const { enqueueNotification } = useNotifications();
  const [callAddClusterVariable, { loading, error }] = useApiCall(
    createClusterVariable,
    {
      suppressErrorNotification: true,
    },
  );

  const { control, handleSubmit, watch } = useForm<FormData>({
    defaultValues: {
      name: "",
      value: "",
      scope: ScopeType.GLOBAL,
      tenantId: "",
    },
    mode: "all",
  });

  const watchedScope = watch("scope");
  const isTenantScoped = watchedScope === ScopeType.TENANT;

  const onSubmit = async (data: FormData) => {
    const { success } = await callAddClusterVariable({
      name: data.name.trim(),
      value: JSON.parse(data.value.trim()),
      scope: data.scope,
      tenantId: isTenantScoped ? data.tenantId : "",
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("clusterVariableCreated"),
        subtitle: t("clusterVariableCreatedSuccessfully", {
          clusterVariableName: data.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("createClusterVariable")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("creatingClusterVariable")}
      confirmLabel={t("createClusterVariable")}
    >
      <Stack orientation="vertical" gap={spacing06}>
        <Controller
          name="name"
          control={control}
          rules={{
            required: t("clusterVariableNameRequired"),
          }}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              label={t("name")}
              placeholder={t("clusterVariableNamePlaceholder")}
              errors={fieldState.error?.message}
              autoFocus
            />
          )}
        />
        <Controller
          name="scope"
          control={control}
          render={({ field }) => (
            <RadioButtonGroup
              name="scopeType"
              legendText={t("scope")}
              orientation="horizontal"
            >
              <RadioButton
                value={ScopeType.GLOBAL as string}
                checked={field.value === ScopeType.GLOBAL}
                onClick={() => field.onChange(ScopeType.GLOBAL)}
                labelText={<span>{t("clusterVariableScopeTypeGlobal")}</span>}
                type="radio"
              />
              <RadioButton
                value={ScopeType.TENANT as string}
                checked={field.value === ScopeType.TENANT}
                onClick={() => field.onChange(ScopeType.TENANT)}
                labelText={<span>{t("clusterVariableScopeTypeTenant")}</span>}
                disabled={isSaaS}
                type="radio"
              />
            </RadioButtonGroup>
          )}
        />
        {isTenantScoped && (
          <Controller
            name="tenantId"
            control={control}
            render={({ field }) => (
              <ClusterVariableTenantDropdown
                tenantId={field.value}
                onChange={(tenantId) => field.onChange(tenantId)}
              />
            )}
          />
        )}
        <Controller
          name="value"
          control={control}
          rules={{
            validate: (value) => {
              if (!value || !value.trim()) {
                return t("clusterVariableValueRequired");
              } else if (!isValid(value.trim())) {
                return t("clusterVariableValueInvalid");
              }

              return true;
            },
          }}
          render={({ field, fieldState }) => (
            <JSONEditor
              {...field}
              label={t("clusterVariableCreateValue")}
              errors={fieldState.error?.message}
              beautify
            />
          )}
        />
      </Stack>
    </FormModal>
  );
};
