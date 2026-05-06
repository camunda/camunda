/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CopilotChat, CopilotSidecar} from '@camunda/copilot-chat';
import {useLocation} from 'react-router-dom';
import {useCopilotAdapter} from './useCopilotAdapter';
import {useCurrentInstanceContext} from './useCurrentInstanceContext';
import {useInstanceGreeting} from './useInstanceGreeting';
import {getSuggestionsForRoute} from './startingPrompts';

const Copilot: React.FC = () => {
  const {sendMessage, stopGeneration, resetConversation, isBusy} =
    useCopilotAdapter();
  const {processInstanceId} = useCurrentInstanceContext();
  useInstanceGreeting();
  const {pathname} = useLocation();
  const suggestions = getSuggestionsForRoute(pathname);

  return (
    <CopilotSidecar
      workareaSelector="#main-content"
      headerSelector="header[aria-label='Camunda Operate']"
    >
      {() => (
        <CopilotChat
          onSendMessage={(message) =>
            sendMessage(
              message,
              processInstanceId !== null ? {processInstanceId} : {},
            )
          }
          onStopGeneration={stopGeneration}
          onResetConversation={resetConversation}
          isBusy={isBusy}
          emptyStateTitle="Camunda Copilot"
          emptyStateDescription="Ask about your processes, instances, or Camunda docs."
          suggestions={suggestions}
        />
      )}
    </CopilotSidecar>
  );
};

export {Copilot};
