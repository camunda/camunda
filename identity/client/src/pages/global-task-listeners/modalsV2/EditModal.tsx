/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  MultiSelect,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { FormModal, UseEntityModalProps } from "src/components/modalV2";
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
  toFormValues,
  toRequestEventTypes,
} from "src/pages/global-task-listeners/utility";
import type {
  CreateGlobalTaskListenerRequestBody,
  GlobalTaskListener,
} from "@camunda/camunda-api-zod-schemas/8.10";

const EditModal: FC<UseEntityModalProps<GlobalTaskListener>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("globalTaskListeners");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(globalTaskListenerMutations.update(qc));

  const { control, handleSubmit, reset } =
    useForm<CreateGlobalTaskListenerRequestBody>({
      defaultValues: toFormValues(entity),
      mode: "all",
    });

  // Reset form when entity changes
  useEffect(() => {
    reset(toFormValues(entity));
  }, [entity, reset]);

  const onSubmit = (data: CreateGlobalTaskListenerRequestBody) => {
    mutate(
      {
        id: entity.id,
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
            title: t("globalTaskListenerUpdated"),
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
      headline={t("editGlobalTaskListener")}
      loading={loading}
      error={error}
      loadingDescription={t("editingGlobalTaskListener")}
      confirmLabel={t("update")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="id"
        control={control}
        render={({ field }) => (
          <TextField {...field} label={t("globalTaskListenerId")} readOnly />
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
                // An existing listener can already have every event type
                // selected; the default cap of 3 would collapse the rest into a
                // "+N more" chip and hide what is stored.
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
                // A listener stored without `afterNonGlobal` has no value to
                // stringify — `String(undefined)` would match no item and leave
                // the trigger blank, so fall through to the placeholder instead.
                value={
                  field.value === undefined ? undefined : String(field.value)
                }
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

export default EditModal;
