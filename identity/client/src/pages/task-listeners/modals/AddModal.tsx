/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { Dropdown, MultiSelect, NumberInput } from "@carbon/react";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import {
  createTaskListener,
  EVENT_TYPE_OPTIONS,
  EventTypeOption,
} from "src/utility/api/task-listeners";
import { useNotifications } from "src/components/notifications";

type FormData = {
  id: string;
  type: string;
  eventTypes: EventTypeOption[];
  retries: number;
  afterNonGlobal: boolean;
  priority: number;
};

const LISTENER_TYPE_PATTERN = /^[a-zA-Z0-9._-]+$/;

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("taskListeners");
  const { enqueueNotification } = useNotifications();
  const [callCreateTaskListener, { loading, error }] = useApiCall(
    createTaskListener,
    {
      suppressErrorNotification: true,
    },
  );

  const { control, handleSubmit, watch, setValue } = useForm<FormData>({
    defaultValues: {
      id: "",
      type: "",
      eventTypes: [],
      retries: 5,
      afterNonGlobal: false,
      priority: 0,
    },
    mode: "all",
  });

  const eventTypes = watch("eventTypes");

  const handleEventTypeChange = (selectedItems: EventTypeOption[]) => {
    const individualTypes = EVENT_TYPE_OPTIONS.filter((opt) => opt !== "all");

    // If "all" was just checked, select all individual types too
    if (selectedItems.includes("all") && !eventTypes.includes("all")) {
      setValue("eventTypes", [...EVENT_TYPE_OPTIONS]); // includes "all" and all individuals
      return;
    }

    // If "all" was just unchecked, uncheck all individual types too
    if (!selectedItems.includes("all") && eventTypes.includes("all")) {
      setValue("eventTypes", []);
      return;
    }

    // If an individual type was unchecked while "all" is checked, uncheck "all" too
    if (
      eventTypes.includes("all") &&
      selectedItems.length < EVENT_TYPE_OPTIONS.length
    ) {
      setValue(
        "eventTypes",
        selectedItems.filter((item) => item !== "all"),
      );
      return;
    }

    // If all individual types are now selected, also select "all"
    const allIndividualSelected = individualTypes.every((type) =>
      selectedItems.includes(type),
    );
    if (allIndividualSelected && !selectedItems.includes("all")) {
      setValue("eventTypes", [...EVENT_TYPE_OPTIONS]); // includes "all" and all individuals
      return;
    }

    setValue("eventTypes", selectedItems);
  };

  const getEventTypeLabel = (eventType: EventTypeOption): string => {
    const labels: Record<EventTypeOption, string> = {
      all: t("eventTypeAll"),
      creating: t("eventTypeCreating"),
      updating: t("eventTypeUpdating"),
      assigning: t("eventTypeAssigning"),
      completing: t("eventTypeCompleting"),
      canceling: t("eventTypeCanceling"),
    };
    return labels[eventType];
  };

  const onSubmit = async (data: FormData) => {
    const eventTypes = data.eventTypes.includes("all")
      ? ["all"]
      : data.eventTypes.filter((type) => type !== "all");

    const { success } = await callCreateTaskListener({
      id: data.id,
      type: data.type,
      eventTypes: eventTypes,
      retries: data.retries,
      afterNonGlobal: data.afterNonGlobal,
      priority: data.priority,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("taskListenerCreated"),
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
      headline={t("createTaskListener")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingTaskListener")}
      confirmLabel={t("create")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="id"
        control={control}
        rules={{
          required: t("taskListenerIdRequired"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("taskListenerId")}
            placeholder={t("taskListenerIdPlaceholder")}
            errors={fieldState.error?.message}
            helperText={t("taskListenerIdHelperText")}
            autoFocus
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
            id="event-type-multiselect"
            titleText={t("eventType")}
            label={
              field.value.length > 0
                ? field.value.includes("all")
                  ? t("eventTypeAll")
                  : field.value.map(getEventTypeLabel).join(", ")
                : t("selectEventTypes")
            }
            items={[...EVENT_TYPE_OPTIONS]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: EventTypeOption[];
            }) => {
              handleEventTypeChange(selectedItems);
            }}
            itemToString={(item: EventTypeOption) => getEventTypeLabel(item)}
            invalid={!!fieldState.error}
            invalidText={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="retries"
        control={control}
        render={({ field, fieldState }) => (
          <NumberInput
            id="retries-input"
            label={t("retries")}
            min={1}
            step={1}
            value={field.value}
            onChange={(_, { value }) => {
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
            id="execution-order-dropdown"
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
            id="priority-input"
            label={t("priority")}
            helperText={t("priorityHelperText")}
            min={0}
            step={1}
            value={field.value}
            onChange={(_, { value }: { value: string | number; direction: string }) => {
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

export default AddModal;
