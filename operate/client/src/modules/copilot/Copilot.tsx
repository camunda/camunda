/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CopilotChat, CopilotSidecar} from '@camunda/copilot-chat';
import {useCopilotAdapter} from './useCopilotAdapter';

const Copilot: React.FC = () => {
  const {sendMessage, stopGeneration, resetConversation, isBusy} =
    useCopilotAdapter();

  return (
    <CopilotSidecar
      workareaSelector="#main-content"
      headerSelector="header[aria-label='Camunda Operate']"
    >
      {() => (
        <CopilotChat
          onSendMessage={(message) => sendMessage(message, {})}
          onStopGeneration={stopGeneration}
          onResetConversation={resetConversation}
          isBusy={isBusy}
          emptyStateTitle="Camunda Copilot"
          emptyStateDescription="Ask about your processes, instances, or Camunda docs."
        />
      )}
    </CopilotSidecar>
  );
};

export {Copilot};
