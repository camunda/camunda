/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Modifications, PlusIcon, MinusIcon} from './styled';

type Props = {
  container: HTMLElement;
  newTokenCount: number;
  cancelledTokenCount: number;
};

const ModificationBadgeOverlay: React.FC<Props> = ({
  container,
  newTokenCount,
  cancelledTokenCount,
}) => {
  if (newTokenCount === 0 && cancelledTokenCount === 0) {
    return null;
  }

  return createPortal(
    <Modifications data-testid="modifications-overlay">
      {newTokenCount > 0 && (
        <>
          <PlusIcon data-testid="badge-plus-icon" />
          {newTokenCount}
        </>
      )}
      {newTokenCount > 0 && cancelledTokenCount > 0 && <>{','}</>}
      {cancelledTokenCount > 0 && (
        <>
          <MinusIcon data-testid="badge-minus-icon" />
          {cancelledTokenCount}
        </>
      )}
    </Modifications>,
    container,
  );
};

export {ModificationBadgeOverlay};
