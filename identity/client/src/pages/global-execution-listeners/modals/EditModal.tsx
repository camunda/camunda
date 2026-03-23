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
  Dropdown,
  InlineNotification,
  MultiSelect,
  NumberInput,
} from "@carbon/react";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import {
  updateGlobalExecutionListener,
  LISTENER_EVENT_TYPES,
  LISTENER_ELEMENT_TYPES,
  LISTENER_CATEGORIES,
} from "src/utility/api/global-execution-listeners";
import type {
  GlobalExecutionListener,
  CreateGlobalExecutionListenerRequestBody,
  GlobalExecutionListenerEventType,
  GlobalExecutionListenerElementType,
  GlobalExecutionListenerCategory,
} from "src/utility/api/global-execution-listeners";
import { useNotifications } from "src/components/notifications";
import {
  getEventTypeLabel,
  getEventTypeLabels,
  getElementTypeLabel,
  getCategoryLabel,
  LISTENER_TYPE_PATTERN,
} from "src/pages/global-execution-listeners/utility";

type FormValues = Omit<
  CreateGlobalExecutionListenerRequestBody,
  "elementTypes" | "categories"
> & {
  elementTypes: GlobalExecutionListenerElementType[];
  categories: GlobalExecutionListenerCategory[];
};

const ELEMENT_TYPES_WITH_ALL = ["all", ...LISTENER_ELEMENT_TYPES] as const;
type ElementTypeWithAll = (typeof ELEMENT_TYPES_WITH_ALL)[number];

const CATEGORIES_WITH_ALL = LISTENER_CATEGORIES;

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

  const { control, handleSubmit, watch, setValue, reset } = useForm<FormValues>(
    {
      defaultValues: {
        id: entity?.id ?? "",
        type: entity?.type ?? "",
        eventTypes: entity?.eventTypes ?? [],
        elementTypes: (entity?.elementTypes ??
          []) as GlobalExecutionListenerElementType[],
        categories: (entity?.categories ??
          []) as GlobalExecutionListenerCategory[],
        retries: entity?.retries ?? 3,
        afterNonGlobal: entity?.afterNonGlobal ?? false,
        priority: entity?.priority ?? 50,
      },
      mode: "all",
    },
  );

  useEffect(() => {
    if (entity) {
      reset({
        id: entity.id,
        type: entity.type,
        eventTypes: entity.eventTypes,
        elementTypes: (entity.elementTypes ??
          []) as GlobalExecutionListenerElementType[],
        categories: (entity.categories ??
          []) as GlobalExecutionListenerCategory[],
        retries: entity.retries,
        afterNonGlobal: entity.afterNonGlobal,
        priority: entity.priority,
      });
    }
  }, [entity, reset]);

  const eventTypes = watch("eventTypes");
  const elementTypes = watch("elementTypes");
  const categories = watch("categories");

  const handleEventTypeChange = (
    selectedItems: GlobalExecutionListenerEventType[],
  ) => {
    const individualTypes = LISTENER_EVENT_TYPES.filter((opt) => opt !== "all");

    if (selectedItems.includes("all") && !eventTypes.includes("all")) {
      setValue("eventTypes", [...LISTENER_EVENT_TYPES]);
      return;
    }

    if (!selectedItems.includes("all") && eventTypes.includes("all")) {
      setValue("eventTypes", []);
      return;
    }

    if (
      eventTypes.includes("all") &&
      selectedItems.length < LISTENER_EVENT_TYPES.length
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
      setValue("eventTypes", [...LISTENER_EVENT_TYPES]);
      return;
    }

    setValue("eventTypes", selectedItems);
  };

  const handleElementTypeChange = (selectedItems: ElementTypeWithAll[]) => {
    const hasAll = selectedItems.includes("all");
    const hadAll = (elementTypes as string[]).includes("all");

    if (hasAll && !hadAll) {
      setValue("elementTypes", [...LISTENER_ELEMENT_TYPES]);
      return;
    }

    if (!hasAll && hadAll) {
      setValue("elementTypes", []);
      return;
    }

    const actualItems = selectedItems.filter(
      (item): item is GlobalExecutionListenerElementType => item !== "all",
    );

    if (hadAll && selectedItems.length < ELEMENT_TYPES_WITH_ALL.length) {
      setValue("elementTypes", actualItems);
      return;
    }

    const allIndividualSelected = LISTENER_ELEMENT_TYPES.every((type) =>
      actualItems.includes(type),
    );
    if (allIndividualSelected && !hasAll) {
      setValue("elementTypes", [...LISTENER_ELEMENT_TYPES]);
      return;
    }

    setValue("elementTypes", actualItems);
  };

  const handleCategoryChange = (
    selectedItems: GlobalExecutionListenerCategory[],
  ) => {
    const individualTypes = CATEGORIES_WITH_ALL.filter((opt) => opt !== "all");

    if (selectedItems.includes("all") && !categories.includes("all")) {
      setValue("categories", [...CATEGORIES_WITH_ALL]);
      return;
    }

    if (!selectedItems.includes("all") && categories.includes("all")) {
      setValue("categories", []);
      return;
    }

    if (
      categories.includes("all") &&
      selectedItems.length < CATEGORIES_WITH_ALL.length
    ) {
      setValue(
        "categories",
        selectedItems.filter(
          (item): item is GlobalExecutionListenerCategory => item !== "all",
        ),
      );
      return;
    }

    const allIndividualSelected = individualTypes.every((type) =>
      selectedItems.includes(type),
    );
    if (allIndividualSelected && !selectedItems.includes("all")) {
      setValue("categories", [...CATEGORIES_WITH_ALL]);
      return;
    }

    setValue("categories", selectedItems);
  };

  const showPerformanceWarning = categories.includes("all");

  const elementTypesWithAll: ElementTypeWithAll[] =
    elementTypes.length === LISTENER_ELEMENT_TYPES.length
      ? ["all", ...elementTypes]
      : elementTypes;

  const onSubmit = async (data: FormValues) => {
    const eventTypesPayload = data.eventTypes.includes("all")
      ? (["all"] as GlobalExecutionListenerEventType[])
      : data.eventTypes.filter((type) => type !== "all");

    const payload: CreateGlobalExecutionListenerRequestBody = {
      id: data.id,
      type: data.type,
      eventTypes: eventTypesPayload,
      retries: data.retries,
      afterNonGlobal: data.afterNonGlobal,
      priority: data.priority,
    };

    if (data.elementTypes.length > 0) {
      payload.elementTypes = data.elementTypes;
    }
    if (data.categories.length > 0) {
      payload.categories = data.categories.includes("all")
        ? ["all"]
        : data.categories.filter((c) => c !== "all");
    }

    const { success } = await callUpdateGlobalExecutionListener(payload);

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
              handleEventTypeChange(selectedItems);
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
        name="elementTypes"
        control={control}
        render={({ field }) => (
          <MultiSelect
            id="element-type-multiselect"
            titleText={t("elementType")}
            label={
              elementTypesWithAll.length > 0
                ? elementTypes.length === LISTENER_ELEMENT_TYPES.length
                  ? t("elementTypeAll")
                  : field.value
                      .map((et: GlobalExecutionListenerElementType) =>
                        getElementTypeLabel(et, t),
                      )
                      .join(", ")
                : t("selectElementTypes")
            }
            items={[...ELEMENT_TYPES_WITH_ALL]}
            selectedItems={elementTypesWithAll}
            onChange={({
              selectedItems,
            }: {
              selectedItems: ElementTypeWithAll[];
            }) => {
              handleElementTypeChange(selectedItems);
            }}
            itemToString={(item: ElementTypeWithAll) =>
              item === "all"
                ? t("elementTypeAll")
                : getElementTypeLabel(
                    item as GlobalExecutionListenerElementType,
                    t,
                  )
            }
            helperText={t("elementTypeHelperText")}
          />
        )}
      />
      <Controller
        name="categories"
        control={control}
        render={({ field }) => (
          <MultiSelect
            id="category-multiselect"
            titleText={t("categories")}
            label={
              field.value.length > 0
                ? field.value.includes("all")
                  ? t("categoryAll")
                  : field.value
                      .map((c: GlobalExecutionListenerCategory) =>
                        getCategoryLabel(c, t),
                      )
                      .join(", ")
                : t("selectCategories")
            }
            items={[...CATEGORIES_WITH_ALL]}
            selectedItems={field.value}
            onChange={({
              selectedItems,
            }: {
              selectedItems: GlobalExecutionListenerCategory[];
            }) => {
              handleCategoryChange(selectedItems);
            }}
            itemToString={(item: GlobalExecutionListenerCategory) =>
              getCategoryLabel(item, t)
            }
            helperText={t("categoriesHelperText")}
          />
        )}
      />
      {showPerformanceWarning && (
        <InlineNotification
          kind="warning"
          title=""
          subtitle={t("performanceWarning")}
          lowContrast
          hideCloseButton
        />
      )}
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

export default EditModal;
