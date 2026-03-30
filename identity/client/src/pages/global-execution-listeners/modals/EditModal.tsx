/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { Dropdown, MultiSelect, NumberInput } from "@carbon/react";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import {
  updateGlobalExecutionListener,
  LISTENER_EVENT_TYPES,
  LISTENER_CATEGORIES,
} from "src/utility/api/global-execution-listeners";
import type {
  CreateGlobalExecutionListenerRequestBody,
  GlobalExecutionListener,
  GlobalExecutionListenerEventType,
  GlobalExecutionListenerCategory,
} from "src/utility/api/global-execution-listeners";
import { useNotifications } from "src/components/notifications";
import {
  getEventTypeLabel,
  getEventTypeLabels,
  LISTENER_TYPE_PATTERN,
} from "src/pages/global-execution-listeners/utility";

const EditModal: FC<UseEntityModalProps<GlobalExecutionListener>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("globalExecutionListeners");
  const { enqueueNotification } = useNotifications();
  const [callUpdateGlobalExecutionListener, { loading, error }] = useApiCall(
    updateGlobalExecutionListener,
    { suppressErrorNotification: true },
  );

  const { control, handleSubmit, reset } =
    useForm<CreateGlobalExecutionListenerRequestBody>({
      defaultValues: {
        id: entity.id,
        type: entity.type,
        eventTypes: entity.eventTypes,
        retries: entity.retries ?? undefined,
        afterNonGlobal: entity.afterNonGlobal ?? undefined,
        priority: entity.priority ?? undefined,
        categories: entity.categories ?? [],
      },
      mode: "all",
    });

  useEffect(() => {
    reset({
      id: entity.id,
      type: entity.type,
      eventTypes: entity.eventTypes,
      retries: entity.retries ?? undefined,
      afterNonGlobal: entity.afterNonGlobal ?? undefined,
      priority: entity.priority ?? undefined,
      categories: entity.categories ?? [],
    });
  }, [entity, reset]);

  const onSubmit = async (
    data: CreateGlobalExecutionListenerRequestBody,
  ) => {
    const { success } = await callUpdateGlobalExecutionListener({
      id: entity.id,
      type: data.type,
      eventTypes: data.eventTypes,
      retries: data.retries,
      afterNonGlobal: data.afterNonGlobal,
      priority: data.priority,
      categories: data.categories,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("globalExecutionListenerUpdated"),
        subtitle: data.type,
      });
      onSuccess();
    }
  };

  const afterNonGlobalOptions = [
    { id: "false", label: t("executionOrderBefore"), value: false },
    { id: "true", label: t("executionOrderAfter"), value: true },
  ];

  return (
    <FormModal
      open={open}
      headline={t("editGlobalExecutionListener")}
      loading={loading}
      error={error}
      loadingDescription={t("editingGlobalExecutionListener")}
      confirmLabel={t("update")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="id"
        control={control}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("globalExecutionListenerId")}
            readOnly
          />
        )}
      />
      <Controller
        name="type"
        control={control}
        rules={{
          required: t("listenerTypeRequired"),
          maxLength: { value: 50, message: t("pleaseEnterValidListenerType") },
          pattern: {
            value: LISTENER_TYPE_PATTERN,
            message: t("pleaseEnterValidListenerType"),
          },
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("listenerType")}
            placeholder={t("listenerTypePlaceholder")}
            errors={fieldState.error?.message}
            helperText={t("listenerTypeHelperText")}
            autoFocus
          />
        )}
      />
      <Controller
        name="eventTypes"
        control={control}
        rules={{
          validate: (value) => value.length > 0 || t("eventTypeRequired"),
        }}
        render={({ field, fieldState }) => (
          <MultiSelect
            id="event-type-multiselect-edit"
            titleText={t("eventType")}
            label={
              field.value.length > 0
                ? getEventTypeLabels(field.value, t)
                : t("selectEventTypes")
            }
            items={[...LISTENER_EVENT_TYPES]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: GlobalExecutionListenerEventType[];
            }) => {
              field.onChange(selectedItems);
            }}
            itemToString={(item: GlobalExecutionListenerEventType) =>
              getEventTypeLabel(item, t)
            }
            invalid={!!fieldState.error}
            invalidText={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="categories"
        control={control}
        render={({ field }) => (
          <MultiSelect
            id="category-multiselect-edit"
            titleText={t("category")}
            label={
              field.value && field.value.length > 0
                ? field.value.join(", ")
                : t("selectCategories")
            }
            items={[...LISTENER_CATEGORIES]}
            selectedItems={field.value ?? []}
            onChange={({
              selectedItems,
            }: {
              selectedItems: GlobalExecutionListenerCategory[];
            }) => {
              field.onChange(selectedItems);
            }}
            itemToString={(item: GlobalExecutionListenerCategory) => item}
          />
        )}
      />
      <Controller
        name="retries"
        control={control}
        render={({ field, fieldState }) => (
          <NumberInput
            id="retries-input-edit"
            label={t("retries")}
            min={1}
            step={1}
            value={field.value}
            onChange={(
              _,
              { value }: { value: string | number; direction: string },
            ) => {
              const numValue =
                typeof value === "string" ? parseInt(value, 10) : value;
              if (!isNaN(numValue)) {
                field.onChange(numValue);
              }
            }}
            invalid={!!fieldState.error}
            invalidText={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="afterNonGlobal"
        control={control}
        render={({ field }) => (
          <Dropdown
            id="execution-order-dropdown-edit"
            titleText={t("executionOrder")}
            label={t("executionOrder")}
            items={afterNonGlobalOptions}
            selectedItem={afterNonGlobalOptions.find(
              (opt) => opt.value === field.value,
            )}
            onChange={({
              selectedItem,
            }: {
              selectedItem: {
                id: string;
                label: string;
                value: boolean;
              } | null;
            }) => {
              if (selectedItem) {
                field.onChange(selectedItem.value);
              }
            }}
            itemToString={(item: { id: string; label: string } | null) =>
              item?.label ?? ""
            }
          />
        )}
      />
      <Controller
        name="priority"
        control={control}
        render={({ field, fieldState }) => (
          <NumberInput
            id="priority-input-edit"
            label={t("priority")}
            helperText={t("priorityHelperText")}
            min={0}
            step={1}
            value={field.value}
            onChange={(
              _,
              { value }: { value: string | number; direction: string },
            ) => {
              const numValue =
                typeof value === "string" ? parseInt(value, 10) : value;
              if (!isNaN(numValue)) {
                field.onChange(numValue);
              }
            }}
            invalid={!!fieldState.error}
            invalidText={fieldState.error?.message}
          />
        )}
      />
    </FormModal>
  );
};

export default EditModal;
