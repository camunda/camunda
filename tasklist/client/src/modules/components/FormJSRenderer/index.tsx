/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import type {Variable} from 'modules/types';
import {FormManager} from 'modules/formManager';
import {mergeVariables} from './mergeVariables';
import {ValidationMessage} from './ValidationMessage';
import {getFieldLabels} from './getFieldLabels';
import {usePrefersReducedMotion} from 'modules/hooks/usePrefersReducedMotion';
import styles from './styles.module.scss';
import '@bpmn-io/form-js-viewer/dist/assets/form-js-base.css';
import '@bpmn-io/form-js-carbon-styles/src/carbon-styles.scss';
import type {FileUploadMetadata} from 'modules/mutations/useUploadDocuments';
import set from 'lodash/set';

type Props = {
  handleSubmit: (variables: Variable[]) => Promise<void>;
  handleFileUpload?: (
    files: Map<string, File[]>,
  ) => Promise<Map<string, FileUploadMetadata[]>>;
  schema: string;
  data?: Record<string, unknown>;
  readOnly?: boolean;
  onMount?: (formManager: FormManager) => void;
  onRender?: () => void;
  onImportError?: () => void;
  onSubmitStart?: () => void;
  onSubmitError?: (error: unknown) => void;
  onSubmitSuccess?: () => void;
  onValidationError?: () => void;
};

function htmlDomId(
  fieldId: string,
  formId?: string,
  indices?: string[],
): string {
  const result = ['fjs-form'];
  if (formId) {
    result.push('-', formId);
  }
  result.push('-', fieldId);
  if (indices) {
    result.push(indices.join(''));
  }
  return result.join('');
}

function useScrollToError(manager: FormManager) {
  const prefersReducedMotion = usePrefersReducedMotion();
  return useCallback(
    (fieldId: string) => {
      if (prefersReducedMotion) {
        return;
      }
      const form = manager.get('form');
      const ffr = manager.get('formFieldRegistry');
      const field = ffr.get(fieldId);
      const indicies: string[] = [];
      if (field._path.length > 2) {
        let parent = field;
        while (parent._path.length > 2) {
          parent = ffr.get(parent._parent);
          if (parent.type === 'dynamiclist') {
            indicies.push('_0');
          }
        }
      }
      let firstInvalidDomId: string | undefined;
      if (field.type === 'radio' || field.type === 'checklist') {
        firstInvalidDomId = htmlDomId(fieldId, form._id, [...indicies, '-0']);
      } else if (field.type === 'datetime') {
        firstInvalidDomId = htmlDomId(fieldId, form._id, [
          ...indicies,
          '-date',
        ]);
      } else {
        firstInvalidDomId = htmlDomId(fieldId, form._id, indicies);
      }
      if (firstInvalidDomId) {
        document
          .getElementById(firstInvalidDomId)
          ?.scrollIntoView({behavior: 'auto', block: 'center'});
      }
    },
    [manager, prefersReducedMotion],
  );
}

function extractFilePath(data: object, path: string = ''): Map<string, string> {
  let paths = new Map();

  if (data === null || data === undefined) {
    return paths;
  }

  for (const [key, value] of Object.entries(data)) {
    const itemPath = path.length === 0 ? key : `${path}.${key}`;

    if (typeof value === 'string' && value.startsWith('files::')) {
      paths.set(value, itemPath);
      continue;
    }

    if (Array.isArray(value)) {
      const result = value
        .map((dynamicListItem, index) =>
          extractFilePath(dynamicListItem, `${itemPath}[${index}]`),
        )
        .reduce((acc, cur) => new Map([...acc, ...cur]), new Map());

      paths = new Map([...paths, ...result]);
      continue;
    }

    if (
      typeof value === 'object' &&
      value !== null &&
      Object.keys(value).length > 0
    ) {
      const result = extractFilePath(
        value,
        path.length === 0 ? key : `${itemPath}.${key}`,
      );
      paths = new Map([...paths, ...result]);
      continue;
    }
  }

  return paths;
}

function injectFileMetadataIntoData(options: {
  data: Record<string, unknown>;
  fileMetadata: Map<string, FileUploadMetadata[]>;
  pathsToInject: Map<string, string>;
}): Record<string, unknown> {
  const {data, fileMetadata, pathsToInject} = options;
  let result = data;

  pathsToInject.forEach((filepickerPath, fileKey) => {
    const metadata = fileMetadata.get(fileKey);

    if (metadata === undefined) {
      return;
    }

    result = set(result, filepickerPath, metadata);
  });

  return result;
}

const FormJSRenderer: React.FC<Props> = ({
  handleSubmit,
  schema,
  data = {},
  readOnly,
  onMount,
  onRender,
  onImportError,
  onSubmitStart,
  onSubmitError,
  onSubmitSuccess,
  onValidationError,
  handleFileUpload = () => Promise.resolve(new Map()),
}) => {
  const formManagerRef = useRef<FormManager>(new FormManager());
  const formContainerRef = useRef<HTMLDivElement | null>(null);
  const [invalidFields, setInvalidFields] = useState<
    {ids: string[]; labels: string[]} | undefined
  >();
  const scrollToError = useScrollToError(formManagerRef.current);

  useEffect(() => {
    const formManager = formManagerRef.current;

    onMount?.(formManager);
  }, [onMount]);

  useEffect(() => {
    function render() {
      const formManager = formManagerRef.current;
      const container = formContainerRef.current;

      if (container !== null) {
        onRender?.();
        formManager.render({
          container,
          schema,
          data,
          onImportError,
          onSubmit: async ({data: newData, errors, files = new Map()}) => {
            onSubmitStart?.();
            setInvalidFields(undefined);
            if (Object.keys(errors).length === 0) {
              const enrichedData =
                files.size === 0
                  ? newData
                  : injectFileMetadataIntoData({
                      data: newData,
                      fileMetadata: await handleFileUpload(files),
                      pathsToInject: extractFilePath(newData),
                    });
              const variables = Object.entries(
                mergeVariables(data, enrichedData),
              ).map(
                ([name, value]) =>
                  ({
                    name,
                    value: JSON.stringify(value),
                  }) as Variable,
              );

              try {
                await handleSubmit(variables);
                onSubmitSuccess?.();
              } catch (error) {
                onSubmitError?.(error);
              }
            } else {
              onValidationError?.();
              const fieldIds = Object.keys(errors);
              setInvalidFields({
                ids: fieldIds,
                labels: getFieldLabels(formManager, fieldIds),
              });
              if (fieldIds.length > 0) {
                scrollToError(fieldIds[0]);
              }
              return;
            }
          },
        });
      }
    }

    render();
  }, [
    schema,
    handleSubmit,
    handleFileUpload,
    onRender,
    onImportError,
    onSubmitStart,
    onSubmitSuccess,
    onSubmitError,
    onValidationError,
    data,
    scrollToError,
  ]);

  useEffect(() => {
    const formManager = formManagerRef.current;

    return () => {
      formManager.detach();
    };
  }, []);

  useEffect(() => {
    const formManager = formManagerRef.current;

    formManager.setReadOnly(Boolean(readOnly));
  }, [readOnly]);

  return (
    <>
      <div className={styles.container}>
        <div ref={formContainerRef} className={styles.formRoot} />
      </div>
      {invalidFields !== undefined ? (
        <ValidationMessage
          fieldIds={invalidFields.ids}
          fieldLabels={invalidFields.labels}
        />
      ) : null}
    </>
  );
};

export {FormJSRenderer};
