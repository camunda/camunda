/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MarkdownMessage} from '../MarkdownMessage';
import {StructuredValue} from './index';
import {splitStructuredText, stripDocumentTags} from './format';
import {Segments} from './styled';

type StructuredTextProps = {
  content: string;
};

/**
 * Renders agent text as markdown, with inline document tags hidden and any
 * embedded JSON shown as labeled fields.
 */
const StructuredText: React.FC<StructuredTextProps> = ({content}) => {
  const segments = splitStructuredText(stripDocumentTags(content));
  if (segments.length === 1 && segments[0]?.kind === 'text') {
    return <MarkdownMessage content={segments[0].text} />;
  }
  return (
    <Segments>
      {segments.map((segment, index) =>
        segment.kind === 'text' ? (
          <MarkdownMessage key={index} content={segment.text} />
        ) : (
          <StructuredValue key={index} value={segment.value} />
        ),
      )}
    </Segments>
  );
};

export {StructuredText};
