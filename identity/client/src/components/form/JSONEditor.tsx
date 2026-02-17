/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor from "@monaco-editor/react";
import { Button, FormLabel, Stack } from "@carbon/react";
import { observer } from "mobx-react-lite";
import { ComponentProps, FC, useEffect, useRef, useState } from "react";
import { beautify as beautifyJSON } from "src/utility/components/editor/jsonUtils.ts";
import { options } from "src/utility/components/editor/options.ts";
import useTranslate from "src/utility/localization";
import { Copy, Edit } from "@carbon/react/icons";
import Flex from "src/components/layout/Flex.tsx";
import { spacing03, supportError } from "@carbon/elements";
import { useNotifications } from "src/components/notifications";

type EditorFirstParam = Parameters<
  NonNullable<ComponentProps<typeof JSONEditor>["onMount"]>
>[0];

type JSONEditorProps = {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
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
    onValidate = () => {},
    onMount = () => {},
  }) => {
    return (
      <Editor
        options={{ ...options, readOnly }}
        language="json"
        value={value}
        height="32vh"
        width="100%"
        onChange={(value) => {
          onChange?.(value ?? "");
        }}
        onMount={(editor, monaco) => {
          editor.focus();

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
    <Stack gap={spacing03}>
      <Flex align="start">
        <Flex
          direction="column"
          align="start"
          spacing="small"
          style={{ flex: 1, minHeight: "36px" }}
        >
          <FormLabel>{label}</FormLabel>
          {errors?.length > 0 && (
            <FormLabel style={{ color: supportError }}>{errors}</FormLabel>
          )}
        </Flex>
        <Flex spacing="small" style={{ alignSelf: "end" }}>
          {beautify && (
            <Button
              onClick={() => onChange?.(beautifyJSON(value))}
              size="sm"
              kind="ghost"
              renderIcon={Edit}
            >
              {t("format")}
            </Button>
          )}
          {copy && (
            <Button onClick={onCopy} size="sm" kind="ghost" renderIcon={Copy}>
              {t("copy")}
            </Button>
          )}
        </Flex>
      </Flex>
      <JSONEditor
        value={value}
        onChange={onChange}
        readOnly={readOnly}
        onValidate={setIsValid}
        onMount={(editor) => {
          editorRef.current = editor;
        }}
      />
    </Stack>
  );
};

export default JSONEditorField;
