/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {Callout} from './styled';
import {storeStateLocally} from 'modules/utils/localStorage';

type Props = {
  type: 'Input' | 'Output';
  title: string;
  subtitle: string;
  onClose: () => void;
};

const IOMappingInfoBanner: React.FC<Props> = ({
  type,
  title,
  subtitle,
  onClose,
}) => {
  const titleId = useId();

  return (
    <Callout
      kind="info"
      lowContrast
      title={title}
      titleId={titleId}
      subtitle={
        <>
          {subtitle}
          <br />
          <br />
          <a
            aria-describedby={titleId}
            href="https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings"
            target="_blank"
            rel="noopener noreferrer"
          >
            Learn more
          </a>
        </>
      }
      actionButtonLabel="Close"
      onActionButtonClick={() => {
        onClose();
        storeStateLocally({[`hide${type}MappingsHelperBanner`]: true});
      }}
    />
  );
};

export {IOMappingInfoBanner};
