/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  MultiSelect,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { FormModal, UseModalProps } from "src/components/modalV2";
import useTranslate from "src/utility/localization";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { globalTaskListenerMutations } from "src/utility/api/global-task-listeners/mutations";
import FormField from "src/components/formV2/FormField";
import TextField from "src/components/formV2/TextField";
import NumberField from "src/components/formV2/NumberField";
import { LISTENER_EVENT_TYPES } from "src/utility/api/global-task-listeners";
import { useNotifications } from "src/components/notifications";
import {
  getEventTypeOptions,
  getExecutionOrderOptions,
  LISTENER_TYPE_PATTERN,
  syncAllEventType,
  toRequestEventTypes,
} from "src/pages/global-task-listeners/utility";
import type { CreateGlobalTaskListenerRequestBody } from "@camunda/camunda-api-zod-schemas/8.10";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("globalTaskListeners");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(globalTaskListenerMutations.create(qc));

  const { control, handleSubmit } =
    useForm<CreateGlobalTaskListenerRequestBody>({
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

  const onSubmit = (data: CreateGlobalTaskListenerRequestBody) => {
    mutate(
      {
        id: data.id,
        type: data.type,
        eventTypes: toRequestEventTypes(data.eventTypes),
        retries: data.retries,
        afterNonGlobal: data.afterNonGlobal,
        priority: data.priority,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("globalTaskListenerCreated"),
            subtitle: data.type,
          });
          onSuccess();
        },
      },
    );
  };

  const afterNonGlobalOptions = getExecutionOrderOptions(t);
  const eventTypeOptions = getEventTypeOptions(t);

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
          <FormField label={t("eventType")} error={fieldState.error?.message}>
            {({ id }) => (
              <MultiSelect
                id={id}
                placeholder={t("selectEventTypes")}
                options={eventTypeOptions}
                // "all" is a real API value listed next to the individual
                // events, so the schema order carries meaning and the built-in
                // select-all row would duplicate the "All events" option.
                sorted={false}
                hideSelectAll
                searchable={false}
                // Checking "All events" selects every type at once, and the
                // default cap of 3 would collapse the rest into a "+N more"
                // chip that hides what is about to be submitted.
                maxCount={LISTENER_EVENT_TYPES.length}
                value={field.value}
                onValueChange={(value) =>
                  field.onChange(syncAllEventType(value, field.value))
                }
              />
            )}
          </FormField>
        )}
      />
      <Controller
        name="retries"
        control={control}
        rules={{ min: { value: 1, message: t("retriesMin") } }}
        render={({ field, fieldState }) => (
          <NumberField
            label={t("retries")}
            helperText={t("retriesHelperText")}
            min={1}
            step={1}
            value={field.value}
            onValueChange={field.onChange}
            error={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="afterNonGlobal"
        control={control}
        render={({ field }) => (
          <FormField label={t("executionOrder")}>
            {({ id }) => (
              <Select
                value={String(field.value)}
                onValueChange={(value) => {
                  field.onChange(value === "true");
                }}
              >
                <SelectTrigger id={id} className="w-full">
                  <SelectValue placeholder={t("executionOrder")} />
                </SelectTrigger>
                <SelectContent>
                  {afterNonGlobalOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>
        )}
      />
      <Controller
        name="priority"
        control={control}
        rules={{ min: { value: 0, message: t("priorityMin") } }}
        render={({ field, fieldState }) => (
          <NumberField
            label={t("priority")}
            helperText={t("priorityHelperText")}
            min={0}
            step={1}
            value={field.value}
            onValueChange={field.onChange}
            error={fieldState.error?.message}
          />
        )}
      />
    </FormModal>
  );
};

export default AddModal;
