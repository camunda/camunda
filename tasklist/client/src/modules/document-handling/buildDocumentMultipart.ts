/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function buildDocumentMultipart(files: Map<string, File[]>): {
  body: Blob;
  headers: Record<string, string>;
} {
  const boundary = `----FormBoundary${Math.random().toString(36).substring(2)}`;
  const contentType = `multipart/form-data; boundary=${boundary}`;
  const startBoundary = `--${boundary}`;
  const endBoundary = `--${boundary}--`;
  const CRLF = '\r\n';
  const parts = Array.from(files.entries())
    .map(([key, files]) => {
      const metadata = {
        customProperties: {
          [PICKER_KEY]: key,
        },
      };

      const pickerParts = files.map((file) => {
        const header = [
          startBoundary,
          `Content-Disposition: form-data; name="files"; filename="${file.name}"`,
          `Content-Type: ${file.type}`,
          `X-Document-Metadata: ${JSON.stringify(metadata)}`,
          '',
          '',
        ].join(CRLF);

        return new Blob([header, file, CRLF], {type: file.type});
      });

      pickerParts.push(new Blob([CRLF, startBoundary, CRLF]));

      return pickerParts;
    })
    .flat();

  parts.pop();
  parts.push(new Blob([endBoundary]));

  const body = new Blob(parts, {
    type: contentType,
  });

  return {
    body,
    headers: {
      'Content-Type': contentType,
    },
  };
}

const PICKER_KEY = 'pickerKey';

export {buildDocumentMultipart, PICKER_KEY};
