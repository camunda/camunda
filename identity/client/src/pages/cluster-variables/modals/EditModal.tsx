/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { beautify, isValid } from "src/utility/components/editor/jsonUtils.ts";
import JSONEditor from "src/components/form/JSONEditor.tsx";
import { useApiCall } from "src/utility/api";
import {
  ScopeType,
  updateClusterVariable,
} from "src/utility/api/cluster-variables";
import { useNotifications } from "src/components/notifications";

type ClusterVariableEntity = {
  name: string;
  value: string;
  scope: ScopeType;
  tenantId?: string;
};

type FormData = {
  value: string;
};

const EditModal: FC<UseEntityModalProps<ClusterVariableEntity>> = ({
  open,
  onClose,
  onSuccess,
  entity: clusterVariable,
}) => {
  const { t } = useTranslate("clusterVariables");
  const { enqueueNotification } = useNotifications();
  const [callUpdateClusterVariable, { loading, error }] = useApiCall(
    updateClusterVariable,
    { suppressErrorNotification: true },
  );
  const initialValue = beautify(clusterVariable.value);

  const {
    control,
    handleSubmit,
    watch,
    formState: { isValid: isFormValid },
  } = useForm<FormData>({
    defaultValues: {
      value: initialValue,
    },
    mode: "all",
  });

  const currentValue = watch("value");
  const hasChanged = currentValue !== initialValue;
  const isSubmitDisabled = !isFormValid || !hasChanged;

  const handleSave = async (data: FormData) => {
    const { success } = await callUpdateClusterVariable({
      name: clusterVariable.name,
      scope: clusterVariable.scope,
      tenantId: clusterVariable.tenantId,
      value: JSON.parse(data.value.trim()),
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("clusterVariableUpdated"),
        subtitle: t("clusterVariableUpdatedSuccessfully", {
          name: clusterVariable.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      onClose={onClose}
      headline={t("editClusterVariable", { name: clusterVariable.name })}
      onSubmit={handleSubmit(handleSave)}
      confirmLabel={t("save")}
      submitDisabled={isSubmitDisabled}
      loading={loading}
      loadingDescription={t("updatingClusterVariable")}
      error={error}
    >
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
            label={t("clusterVariableDetailsValue")}
            errors={fieldState.error?.message}
            beautify
          />
        )}
      />
    </FormModal>
  );
};

export default EditModal;
