/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor from "@monaco-editor/react";
import { Button, Label, Text } from "@camunda/design-system";
import { observer } from "mobx-react-lite";
import { ComponentProps, FC, useEffect, useRef, useState } from "react";
import { Copy, Pencil } from "lucide-react";
import { beautify as beautifyJSON } from "src/utility/components/editor/jsonUtils.ts";
import { options } from "src/utility/components/editor/options.ts";
import useTranslate from "src/utility/localization";
import { useNotifications } from "src/components/notifications";
import { themeStore } from "src/common/theme/theme.ts";

type EditorFirstParam = Parameters<
  NonNullable<ComponentProps<typeof JSONEditor>["onMount"]>
>[0];

type JSONEditorProps = {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  autoFocus?: boolean;
  onValidate?: (isValid: boolean) => void;
  onMount?: (editor: {
    showMarkers: () => void;
    hideMarkers: () => void;
  }) => void;
};

const JSONEditor: FC<JSONEditorProps> = observer(
  ({
    value,
    onChange,
    readOnly = false,
    autoFocus = false,
    onValidate = () => {},
    onMount = () => {},
  }) => {
    return (
      <Editor
        className="w-full h-86"
        options={{ ...options, readOnly }}
        language="json"
        theme={themeStore.actualTheme === "dark" ? "vs-dark" : "light"}
        value={value}
        onChange={(value) => {
          onChange?.(value ?? "");
        }}
        onMount={(editor, monaco) => {
          if (autoFocus) {
            editor.focus();
          }

          onMount({
            showMarkers: () => {
              editor.trigger("", "editor.action.marker.next", undefined);
              editor.trigger("", "editor.action.marker.prev", undefined);
            },
            hideMarkers: () => {
              editor.trigger("", "closeMarkersNavigation", undefined);
            },
          });

          monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
            ...monaco.languages.json.jsonDefaults.diagnosticsOptions,
            schemaValidation: "error",
            schemaRequest: "error",
          });
        }}
        onValidate={(markers) => {
          onValidate(markers.length === 0);
        }}
      />
    );
  },
);

type JSONEditorFieldProps = {
  label: string;
  value: string;
  errors?: string[] | string;
  readOnly?: boolean;
  autoFocus?: boolean;
  onChange?: (newValue: string) => void;
  beautify?: boolean;
  copy?: boolean;
  copyProps?: { notificationText?: string; onClick?: () => void };
};

const JSONEditorField: FC<JSONEditorFieldProps> = ({
  label,
  value,
  errors = [],
  readOnly = false,
  autoFocus = false,
  onChange,
  beautify = false,
  copy = false,
  copyProps,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [isValid, setIsValid] = useState(true);
  const editorRef = useRef<EditorFirstParam | null>(null);

  useEffect(() => {
    if (isValid) {
      // This will hide the problems dialog if the user has it opened. This does not hide by
      // default even after the problems are resolved. If the json becomes invalid again, the user
      // can reopen this dialog to see details.
      editorRef.current?.hideMarkers();
    }
  }, [isValid]);

  const onCopy = async () => {
    await navigator.clipboard.writeText(value);

    if (copyProps?.notificationText) {
      enqueueNotification({
        kind: "info",
        title: copyProps.notificationText,
      });
    }

    copyProps?.onClick?.();
  };

  return (
    <div className="flex flex-col">
      <div className="flex items-start gap-3">
        <div className="flex min-h-9 flex-1 flex-col items-start gap-1">
          <Label>{label}</Label>
          {errors?.length > 0 && (
            <Text
              as="p"
              variant="helper"
              role="alert"
              className="text-danger-action-default"
            >
              {errors}
            </Text>
          )}
        </div>
        <div className="flex items-center gap-1 self-end">
          {beautify && (
            <Button
              type="button"
              onClick={() => onChange?.(beautifyJSON(value))}
              size="sm"
              variant="ghost"
            >
              <Pencil aria-hidden="true" />
              {t("format")}
            </Button>
          )}
          {copy && (
            <Button type="button" onClick={onCopy} size="sm" variant="ghost">
              <Copy aria-hidden="true" />
              {t("copy")}
            </Button>
          )}
        </div>
      </div>
      <JSONEditor
        value={value}
        onChange={onChange}
        readOnly={readOnly}
        autoFocus={autoFocus}
        onValidate={setIsValid}
        onMount={(editor) => {
          editorRef.current = editor;
        }}
      />
    </div>
  );
};

export default JSONEditorField;
