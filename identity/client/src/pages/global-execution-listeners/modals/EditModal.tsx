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
  GlobalExecutionListener,
} from "src/utility/api/global-execution-listeners";
import { useNotifications } from "src/components/notifications";
import {
  getEventTypeLabel,
  getEventTypeLabels,
  getCategoryLabel,
  getCategoryLabels,
  LISTENER_TYPE_PATTERN,
  EXECUTION_LISTENER_EVENT_TYPES,
  ELEMENT_CATEGORIES,
} from "src/pages/global-execution-listeners/utility";
import type {
  ExecutionListenerEventType,
  ElementCategory,
} from "src/pages/global-execution-listeners/utility";

type FormData = {
  id: string;
  type: string;
  eventTypes: ExecutionListenerEventType[];
  categories: ElementCategory[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
};

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
    {
      suppressErrorNotification: true,
    },
  );

  const { control, handleSubmit, watch, setValue, reset } = useForm<FormData>({
    defaultValues: {
      id: entity.id,
      type: entity.type,
      eventTypes: (entity.eventTypes ?? []) as ExecutionListenerEventType[],
      categories: (entity.categories ?? []) as ElementCategory[],
      retries: entity.retries ?? 3,
      afterNonGlobal: entity.afterNonGlobal ?? false,
      priority: entity.priority ?? 50,
    },
    mode: "all",
  });

  useEffect(() => {
    reset({
      id: entity.id,
      type: entity.type,
      eventTypes: (entity.eventTypes ?? []) as ExecutionListenerEventType[],
      categories: (entity.categories ?? []) as ElementCategory[],
      retries: entity.retries ?? 3,
      afterNonGlobal: entity.afterNonGlobal ?? false,
      priority: entity.priority ?? 50,
    });
  }, [entity, reset]);

  const eventTypes = watch("eventTypes");
  const categories = watch("categories");

  const handleEventTypeChange = (
    selectedItems: ExecutionListenerEventType[],
  ) => {
    const individualTypes = EXECUTION_LISTENER_EVENT_TYPES.filter(
      (opt) => opt !== "all",
    );

    if (selectedItems.includes("all") && !eventTypes.includes("all")) {
      setValue("eventTypes", [...EXECUTION_LISTENER_EVENT_TYPES]);
      return;
    }

    if (!selectedItems.includes("all") && eventTypes.includes("all")) {
      setValue("eventTypes", []);
      return;
    }

    if (
      eventTypes.includes("all") &&
      selectedItems.length < EXECUTION_LISTENER_EVENT_TYPES.length
    ) {
      setValue(
        "eventTypes",
        selectedItems.filter((item) => item !== "all"),
      );
      return;
    }

    const allIndividualSelected = individualTypes.every((type) =>
      selectedItems.includes(type),
    );
    if (allIndividualSelected && !selectedItems.includes("all")) {
      setValue("eventTypes", [...EXECUTION_LISTENER_EVENT_TYPES]);
      return;
    }

    setValue("eventTypes", selectedItems);
  };

  const handleCategoryChange = (selectedItems: ElementCategory[]) => {
    const individualCategories = ELEMENT_CATEGORIES.filter(
      (opt) => opt !== "all",
    );

    if (selectedItems.includes("all") && !categories.includes("all")) {
      setValue("categories", [...ELEMENT_CATEGORIES]);
      return;
    }

    if (!selectedItems.includes("all") && categories.includes("all")) {
      setValue("categories", []);
      return;
    }

    if (
      categories.includes("all") &&
      selectedItems.length < ELEMENT_CATEGORIES.length
    ) {
      setValue(
        "categories",
        selectedItems.filter((item) => item !== "all"),
      );
      return;
    }

    const allIndividualSelected = individualCategories.every((cat) =>
      selectedItems.includes(cat),
    );
    if (allIndividualSelected && !selectedItems.includes("all")) {
      setValue("categories", [...ELEMENT_CATEGORIES]);
      return;
    }

    setValue("categories", selectedItems);
  };

  const onSubmit = async (data: FormData) => {
    const eventTypes = data.eventTypes.includes("all")
      ? ["all"]
      : data.eventTypes.filter((type) => type !== "all");

    const categories = data.categories.includes("all")
      ? ["all"]
      : data.categories.filter((cat) => cat !== "all");

    const { success } = await callUpdateGlobalExecutionListener({
      id: entity.id,
      type: data.type,
      eventTypes: eventTypes,
      categories: categories.length > 0 ? categories : undefined,
      retries: data.retries,
      afterNonGlobal: data.afterNonGlobal,
      priority: data.priority,
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
          maxLength: {
            value: 50,
            message: t("pleaseEnterValidListenerType"),
          },
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
            items={[...EXECUTION_LISTENER_EVENT_TYPES]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: ExecutionListenerEventType[];
            }) => {
              handleEventTypeChange(selectedItems);
            }}
            itemToString={(item: ExecutionListenerEventType) =>
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
            id="categories-multiselect-edit"
            titleText={t("categories")}
            label={
              field.value.length > 0
                ? getCategoryLabels(field.value, t)
                : t("selectCategories")
            }
            items={[...ELEMENT_CATEGORIES]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: ElementCategory[];
            }) => {
              handleCategoryChange(selectedItems);
            }}
            itemToString={(item: ElementCategory) => getCategoryLabel(item, t)}
            helperText={t("categoriesHelperText")}
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
