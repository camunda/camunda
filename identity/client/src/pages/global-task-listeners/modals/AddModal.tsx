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
  createGlobalTaskListener,
  LISTENER_EVENT_TYPES,
  ListenerEventType,
} from "src/utility/api/global-task-listeners";
import { useNotifications } from "src/components/notifications";
import {
  FormData,
  LISTENER_TYPE_PATTERN,
} from "src/pages/global-task-listeners";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("globalTaskListeners");
  const { enqueueNotification } = useNotifications();
  const [callCreateGlobalTaskListener, { loading, error }] = useApiCall(
    createGlobalTaskListener,
    {
      suppressErrorNotification: true,
    },
  );

  const { control, handleSubmit, watch, setValue } = useForm<FormData>({
    defaultValues: {
      id: "",
      type: "",
      eventTypes: [],
      retries: 3,
      afterNonGlobal: false,
      priority: 50,
    },
    mode: "all",
  });

  const eventTypes = watch("eventTypes");

  const handleEventTypeChange = (selectedItems: ListenerEventType[]) => {
    const individualTypes = LISTENER_EVENT_TYPES.filter(
      (opt) => opt !== ListenerEventType.ALL,
    );

    // If "all" was just checked, select all individual types too
    if (
      selectedItems.includes(ListenerEventType.ALL) &&
      !eventTypes.includes(ListenerEventType.ALL)
    ) {
      setValue("eventTypes", [...LISTENER_EVENT_TYPES]); // includes "all" and all individuals
      return;
    }

    // If "all" was just unchecked, uncheck all individual types too
    if (
      !selectedItems.includes(ListenerEventType.ALL) &&
      eventTypes.includes(ListenerEventType.ALL)
    ) {
      setValue("eventTypes", []);
      return;
    }

    // If an individual type was unchecked while "all" is checked, uncheck "all" too
    if (
      eventTypes.includes(ListenerEventType.ALL) &&
      selectedItems.length < LISTENER_EVENT_TYPES.length
    ) {
      setValue(
        "eventTypes",
        selectedItems.filter((item) => item !== ListenerEventType.ALL),
      );
      return;
    }

    // If all individual types are now selected, also select "all"
    const allIndividualSelected = individualTypes.every((type) =>
      selectedItems.includes(type),
    );
    if (
      allIndividualSelected &&
      !selectedItems.includes(ListenerEventType.ALL)
    ) {
      setValue("eventTypes", [...LISTENER_EVENT_TYPES]); // includes "all" and all individuals
      return;
    }

    setValue("eventTypes", selectedItems);
  };

  const getEventTypeLabel = (eventType: ListenerEventType): string => {
    const labels: Record<ListenerEventType, string> = {
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
    const eventTypes = data.eventTypes.includes(ListenerEventType.ALL)
      ? [ListenerEventType.ALL]
      : data.eventTypes.filter((type) => type !== ListenerEventType.ALL);

    const { success } = await callCreateGlobalTaskListener({
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
        title: t("globalTaskListenerCreated"),
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
      headline={t("createGlobalTaskListener")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingGlobalTaskListener")}
      confirmLabel={t("create")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="id"
        control={control}
        rules={{
          required: t("globalTaskListenerIdRequired"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("globalTaskListenerId")}
            placeholder={t("globalTaskListenerIdPlaceholder")}
            errors={fieldState.error?.message}
            helperText={t("globalTaskListenerIdHelperText")}
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
                ? field.value.includes(ListenerEventType.ALL)
                  ? t("eventTypeAll")
                  : field.value.map(getEventTypeLabel).join(", ")
                : t("selectEventTypes")
            }
            items={[...LISTENER_EVENT_TYPES]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: ListenerEventType[];
            }) => {
              handleEventTypeChange(selectedItems);
            }}
            itemToString={(item: ListenerEventType) => getEventTypeLabel(item)}
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

export default AddModal;
