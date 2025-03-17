/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildDocumentMultipart} from 'common/document-handling/buildDocumentMultipart';

async function blobToString(blob: Blob): Promise<string> {
  return new Promise<string>((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsText(blob);
  });
}

describe('buildDocumentMultipart', () => {
  it('should build a multipart/form-data body and headers', async () => {
    const files = new Map([
      ['foobar', [new File(['test'], 'test.txt', {type: 'text/plain'})]],
    ]);
    const {body, headers} = buildDocumentMultipart(files);

    expect(headers['Content-Type']).toMatch(
      /^multipart\/form-data.*boundary=.+/,
    );

    const content = await blobToString(body);

    expect(content).toContain(
      'Content-Disposition: form-data; name="files"; filename="test.txt"',
    );
    expect(content).toContain('Content-Type: text/plain');
    expect(content).toContain(
      'X-Document-Metadata: {"customProperties":{"pickerKey":"foobar"}}',
    );
    expect(content).toContain('test');
  });
});
