import { JSX } from 'react/jsx-runtime';
import { layoutProcess } from 'bpmn-auto-layout';
import { MarkdownProps } from '@carbon/ai-chat-components/es/react/markdown';
import { ReactNode } from 'react';
import { StoreApi } from 'zustand';
import { UseBoundStore } from 'zustand';

export declare const adaptBpmnLintErrorsForLlm: (rawLintErrors: ReadonlyArray<RawBpmnLintError>) => ReadonlyArray<LintError>;

export declare interface AgentAdapterConfig {
    readonly transport: AgentTransport;
    readonly onToolInvoke?: (toolName: string, args: Record<string, unknown>) => Promise<unknown>;
    readonly getStatusLabel?: (eventType: EventTypeValue, toolName?: string, eventStatus?: EventStatusValue) => string;
}

export declare interface AgentAdapterReturn {
    readonly sendMessage: (content: string, context: Record<string, unknown>) => Promise<void>;
    readonly stopGeneration: () => void;
    readonly resetConversation: () => void;
    readonly loadConversation: (conversationId: string) => Promise<void>;
    readonly isBusy: boolean;
}

export declare interface AgentEvent {
    readonly conversationId: string;
    readonly type: EventTypeValue;
    readonly status: EventStatusValue;
    readonly content?: string;
    readonly toolName?: string;
    readonly toolCallId?: string;
    readonly toolArguments?: string;
    readonly toolResult?: string;
}

export declare type AgentResult = ToolInvokeResult | MixedToolResultEvent | ConversationTitleResult;

export declare const AgentState: {
    readonly IDLE: "IDLE";
    readonly BUSY: "BUSY";
    readonly AWAITING_TOOL: "AWAITING_TOOL";
    readonly ERROR: "ERROR";
};

export declare type AgentStateType = (typeof AgentState)[keyof typeof AgentState];

declare type AgentStore = AgentStoreState & AgentStoreActions & {
    callbacks: AgentStoreCallbacks;
};

declare interface AgentStoreActions {
    readonly startConversation: (conversationId: string, messageId: string) => void;
    readonly processEvent: (event: AgentEvent) => AgentResult | null;
    readonly clearPendingTool: () => void;
    readonly setError: (error: string) => void;
    readonly reset: () => void;
    readonly setCallbacks: (callbacks: AgentStoreCallbacks) => void;
    readonly setConversationId: (conversationId: string) => void;
}

declare interface AgentStoreCallbacks {
    readonly onMessageStatusUpdate?: MessageStatusCallback;
    readonly onStatusLabelClear?: StatusLabelCallback;
    readonly onMessageError?: MessageErrorCallback;
}

declare interface AgentStoreState {
    readonly conversationId: string | null;
    readonly currentMessageId: string | null;
    readonly isBusy: boolean;
    readonly currentEventType: EventTypeValue | null;
    readonly currentToolName: string | null;
    readonly pendingToolInvoke: {
        readonly toolName: string;
        readonly toolCallId?: string;
        readonly toolArguments: Record<string, unknown>;
    } | null;
    readonly error: string | null;
}

export declare interface AgentTransport {
    subscribe(conversationId: string, onEvent: (event: AgentEvent) => void): void;
    unsubscribe(conversationId: string): void;
    sendMessage(payload: SendMessagePayload): Promise<void>;
    sendToolResult(payload: ToolResultPayload): Promise<void>;
    haltConversation?(conversationId: string): Promise<void>;
    updateConversation?(conversationId: string, payload: UpdateConversationPayload): Promise<void>;
    deleteConversation?(conversationId: string): Promise<void>;
    fetchConversations?(page: number, size: number): Promise<ConversationsPage>;
    fetchConversationMessages?(conversationId: string, cursor?: number | null, limit?: number): Promise<ConversationMessagesPage>;
    rollbackConversation?(conversationId: string, milestoneId: string): Promise<RollbackResponse | undefined>;
}

declare interface BackendToolPayload {
    readonly name: string;
    readonly description: string;
    readonly parameters: Record<string, unknown>;
    readonly category: string;
}

export declare interface BpmnElement {
    readonly id: string;
    readonly type: string;
    readonly businessObject: Record<string, unknown>;
    readonly parent?: BpmnElement;
    readonly incoming?: ReadonlyArray<BpmnElement>;
    readonly outgoing?: ReadonlyArray<BpmnElement>;
    readonly children?: ReadonlyArray<BpmnElement>;
}

export declare interface BpmnModeler {
    readonly get: (serviceName: string, strict?: boolean) => unknown;
    readonly saveXML: (options?: {
        format?: boolean;
    }) => Promise<{
        xml: string;
    }>;
}

export declare const camundaDocsSearchTool: {
    readonly name: "camunda_docs_search";
    readonly displayName: "Searching docs";
    readonly description: string;
    readonly parameters: {
        readonly type: "object";
        readonly properties: {
            readonly query: {
                readonly type: "string";
                readonly description: "The search query to find information in the Camunda knowledge base";
            };
        };
        readonly required: readonly ["query"];
    };
    readonly type: "frontend";
    readonly contentType: "TEXT";
    readonly category: "GENERAL";
    readonly handler: ToolHandler;
};

export declare interface camundaDocsSearchToolResult {
    readonly answer: string;
    readonly sources: readonly KapaAiSearchResult[];
}

export declare const CAPABILITY: {
    readonly BPMN_EDITING: "BPMN_EDITING";
    readonly FORM_EDITING: "FORM_EDITING";
    readonly DMN_EDITING: "DMN_EDITING";
    readonly FEEL_EDITING: "FEEL_EDITING";
    readonly BPMN_VIEWING: "BPMN_VIEWING";
    readonly FORM_VIEWING: "FORM_VIEWING";
    readonly DMN_VIEWING: "DMN_VIEWING";
    readonly FILE_OPERATIONS: "FILE_OPERATIONS";
    readonly FILE_CREATION: "FILE_CREATION";
    readonly VALIDATION: "VALIDATION";
    readonly INTEGRATION: "INTEGRATION";
};

export declare type Capability = (typeof CAPABILITY)[keyof typeof CAPABILITY];

declare interface ChatActions {
    readonly addUserMessage: (content: string) => string;
    readonly addAssistantMessage: (id: string) => void;
    readonly appendToMessage: (id: string, content: string) => void;
    readonly setMessageContent: (id: string, content: string) => void;
    readonly updateMessageStatus: (id: string, status: MessageStatus) => void;
    readonly updateMessageStatusLabel: (id: string, statusLabel: string) => void;
    readonly setMessageSources: (id: string, sources: readonly SourceReference[]) => void;
    readonly setStreaming: (isStreaming: boolean, messageId?: string | null) => void;
    readonly clearMessages: () => void;
    readonly addThinkingBlock: (messageId: string, blockId: string, label: string) => void;
    readonly appendToThinkingBlock: (messageId: string, blockId: string, content: string) => void;
    readonly completeThinkingBlock: (messageId: string, blockId: string) => void;
    readonly completeAllThinkingBlocks: (messageId: string) => void;
    readonly setMessageError: (id: string, errorMessage: string) => void;
    readonly addToolBlock: (messageId: string, blockId: string, toolName: string, stepNumber: number, toolArguments?: Record<string, unknown>) => void;
    readonly updateToolBlockStatus: (messageId: string, blockId: string, status: ToolBlockStatus) => void;
    readonly updateToolBlockResult: (messageId: string, blockId: string, toolResult: string) => void;
    readonly addErrorBlock: (messageId: string, blockId: string, errorMessage: string) => void;
    readonly setView: (view: ChatView_2) => void;
    readonly setConversationTitle: (title: string) => void;
    readonly loadConversation: (messages: readonly ChatMessage_2[], title: string | null) => void;
    readonly prependMessages: (messages: readonly ChatMessage_2[]) => void;
    readonly rollbackToMessage: (messageId: string) => void;
    readonly addRollbackMessage: (content: string, milestoneId: string) => void;
    readonly setLastAssistantMessageMilestone: (milestoneId: string) => void;
    readonly setLastUserMessageMilestone: (milestoneId: string) => void;
}

export declare const ChatInput: ({ onSend, onStop, isBusy, isDisabled, placeholder, isOpen, }: ChatInputProps) => JSX.Element;

declare interface ChatInputProps {
    readonly onSend: (message: string) => void;
    readonly onStop?: () => void;
    readonly isBusy?: boolean;
    readonly isDisabled?: boolean;
    readonly placeholder?: string;
    readonly isOpen?: boolean;
}

export declare const ChatMessage: ({ message, isBusy }: ChatMessageProps) => JSX.Element;

declare interface ChatMessage_2 {
    readonly id: string;
    readonly role: MessageRole;
    readonly content: string;
    readonly status: MessageStatus;
    readonly timestamp?: number;
    readonly statusLabel?: string;
    readonly thinkingBlocks?: readonly ThinkingBlock[];
    readonly sources?: readonly SourceReference[];
    readonly milestoneId?: string;
}
export { ChatMessage_2 as ChatMessageInterface }
export { ChatMessage_2 as ChatMessageType }

export declare const ChatMessageList: ({ messages, emptyStateTitle, emptyStateDescription, suggestions, onSuggestionClick, onRollback, onSeeVersion, isBusy, hasOlderMessages, isLoadingOlderMessages, onLoadOlderMessages, }: ChatMessageListProps) => JSX.Element;

declare interface ChatMessageListProps {
    readonly messages: readonly ChatMessage_2[];
    readonly emptyStateTitle?: string;
    readonly emptyStateDescription?: ReactNode;
    readonly suggestions?: readonly Suggestion[];
    readonly onSuggestionClick?: (suggestion: Suggestion) => void;
    readonly onRollback?: (milestoneId: string) => void;
    readonly onSeeVersion?: (milestoneId: string) => void;
    readonly isBusy?: boolean;
    readonly hasOlderMessages?: boolean;
    readonly isLoadingOlderMessages?: boolean;
    readonly onLoadOlderMessages?: () => void;
}

declare interface ChatMessageProps {
    readonly message: ChatMessage_2;
    readonly isBusy?: boolean;
}

declare interface ChatState {
    readonly view: ChatView_2;
    readonly messages: readonly ChatMessage_2[];
    readonly isStreaming: boolean;
    readonly streamingMessageId: string | null;
    readonly currentThinkingBlockId: string | null;
    readonly conversationTitle: string | null;
}

declare type ChatStore = ChatState & ChatActions;

declare type ChatView = 'chat' | 'history';

declare type ChatView_2 = 'chat' | 'history';

export declare const closeSidecar: () => void;

export declare type ContentType = 'TEXT' | 'BPMN';

export declare interface ContextItem {
    readonly id?: string;
    readonly label: string;
}

export declare interface Conversation {
    readonly id: string;
    readonly name: string;
    readonly created: string;
    readonly updated: string;
}

export declare interface ConversationMessage {
    readonly id: string;
    readonly type: CopilotMessageType;
    readonly message?: string;
    readonly feel?: string;
    readonly artifactXml?: string;
    readonly artifactJson?: string;
    readonly toolCallId?: string;
    readonly toolName?: string;
    readonly milestoneId?: string;
    readonly created: string;
}

export declare interface ConversationMessagesPage {
    readonly id: string;
    readonly name: string;
    readonly messages: readonly ConversationMessage[];
    readonly nextCursor: number | null;
    readonly hasMore: boolean;
    readonly totalCount: number;
}

export declare interface ConversationsPage {
    readonly conversations: readonly Conversation[];
    readonly page: number;
    readonly size: number;
    readonly totalItems: number;
}

declare interface ConversationTitleResult {
    readonly type: typeof ResultType.CONVERSATION_TITLE;
    readonly conversationTitle: string;
}

export declare const CopilotChat: ({ onSendMessage, kapaAiIntegrationId, onStopGeneration, onResetConversation, isBusy, isDisabled, isOpen, emptyStateTitle, emptyStateDescription, suggestions, className, contextItems, onRemoveContext, onSeeVersion, }: CopilotChatProps) => JSX.Element;

export declare interface CopilotChatProps {
    readonly kapaAiIntegrationId?: string | null;
    readonly onSendMessage: (message: string) => void;
    readonly onStopGeneration?: () => void;
    readonly onResetConversation?: () => void;
    readonly onLoadConversation?: (conversationId: string) => Promise<void>;
    readonly isBusy?: boolean;
    readonly isOpen?: boolean;
    readonly isDisabled?: boolean;
    readonly emptyStateTitle?: string;
    readonly emptyStateDescription?: ReactNode;
    readonly suggestions?: ReadonlyArray<Suggestion>;
    readonly className?: string;
    readonly contextItems?: ReadonlyArray<ContextItem>;
    readonly onRemoveContext?: (item: ContextItem) => void;
    readonly onSeeVersion?: (milestoneId: string) => void;
}

export declare const CopilotHeader: ({ onReset, onShowHistory, conversationTitle, view, isBusy, }: CopilotHeaderProps) => JSX.Element;

declare interface CopilotHeaderProps {
    onReset?: () => void;
    onShowHistory?: () => void;
    conversationTitle?: string;
    view?: ChatView;
    isBusy?: boolean;
}

declare type CopilotMessageType = 'USER' | 'ASSISTANT' | 'TOOL_CALL' | 'TOOL_OUTPUT' | 'SYSTEM';

export declare const CopilotSidecar: ({ children, workareaSelector, headerSelector, zIndex }: CopilotSidecarProps) => JSX.Element;

export declare interface CopilotSidecarProps {
    readonly children: (props: SidecarRenderProps) => ReactNode;
    readonly workareaSelector?: string;
    readonly headerSelector?: string;
    readonly zIndex?: number;
}

export declare interface CopilotToolContext {
    readonly modeler: ModelerAccess;
    readonly files: {
        readonly getContent: (fileId: string) => Promise<string>;
        readonly create: (params: {
            readonly name: string;
            readonly type: string;
            readonly content: string;
            readonly projectId: string;
            readonly folderId: string | null;
        }) => Promise<{
            readonly id: string;
        }>;
        readonly list: (projectId: string) => Promise<ReadonlyArray<FileInfo>>;
    };
    readonly templates: {
        readonly getCustom: () => ReadonlyArray<ElementTemplate>;
        readonly getOotbConnectors: () => ReadonlyArray<ElementTemplate>;
        readonly apply: (elementId: string, templateId: string, version?: number) => unknown;
    };
    readonly form: {
        readonly getContent: () => string | null;
        readonly isAvailable: () => boolean;
        readonly getLintErrors: () => Promise<ReadonlyArray<FormLintError>>;
        readonly importSchema: (content: unknown) => void;
        readonly update: () => Promise<void>;
        readonly getParsedContent: (json: string) => unknown;
        readonly getSchema: () => unknown;
        readonly getSelection: () => unknown;
    };
    readonly dmn: {
        readonly createModdle: () => DmnModdle;
        readonly getModeler: () => DmnModeler;
    };
    readonly feel: {
        readonly validate: (expression: string) => Promise<FeelValidationResult>;
    };
    readonly diagram: {
        readonly isBPMN: () => boolean;
        readonly isDMN: () => boolean;
        readonly canEdit: () => boolean;
        readonly getContent: () => string | null;
        readonly importContent: (xml: string) => Promise<ImportResult>;
        readonly saveContent: () => Promise<void>;
        readonly exportXml: () => Promise<string>;
        readonly getLintErrors: () => Promise<ReadonlyArray<LintError>>;
        readonly getDeploymentErrors: () => ReadonlyArray<unknown>;
        readonly ensureExecutionPlatform: () => void;
    };
    readonly project: {
        readonly resolve: () => ProjectContext;
    };
}

export declare const createAllTools: (context: CopilotToolContext) => ReadonlyArray<ToolRegistration_2>;

export declare const createModelerAccess: ({ getModeler, is, getBusinessObject }: ModelerAccessOptions) => ModelerAccess;

export declare const createWithAutoLayoutAndValidation: (context: CopilotToolContext, layoutProcess: (xml: string) => Promise<string>) => <T extends Record<string, unknown>>(mutationFn: (modeler: BpmnModeler) => T | Promise<T>) => Promise<T & {
    readonly validationErrors: ReadonlyArray<LintError>;
}>;

export declare const createWithValidation: (context: CopilotToolContext) => <T extends Record<string, unknown>>(mutationFn: (modeler: BpmnModeler) => T | Promise<T>) => Promise<T & {
    readonly validationErrors: ReadonlyArray<LintError>;
}>;

export declare type DescriptionFormatter = (args: Record<string, unknown>) => string;

declare interface DmnDefinitions extends DmnModdleElement {
    readonly $model: {
        readonly toXML: (definitions: DmnDefinitions, options?: {
            format?: boolean;
        }) => Promise<{
            xml: string;
        }>;
    };
    readonly drgElement?: ReadonlyArray<DmnModdleElement>;
}

declare interface DmnModdle {
    readonly fromXML: (xml: string) => Promise<{
        rootElement: DmnDefinitions;
    }>;
    readonly create: (type: string, properties: Record<string, unknown>) => DmnModdleElement;
}

declare interface DmnModdleElement extends Record<string, unknown> {
    readonly $type: string;
    readonly id?: string;
}

declare interface DmnModeler {
    readonly get: (serviceName: string, strict?: boolean) => unknown;
    readonly getActiveViewer?: () => DmnViewer | null;
    readonly getViews?: () => ReadonlyArray<unknown>;
    readonly saveXML?: (options?: {
        format?: boolean;
    }) => Promise<{
        xml: string;
    }>;
}

declare interface DmnViewer {
    readonly get: (serviceName: string, strict?: boolean) => unknown;
}

export declare interface ElementTemplate {
    readonly id: string;
    readonly name: string;
    readonly description?: string;
    readonly version: number;
    readonly appliesTo: ReadonlyArray<string>;
    readonly elementType?: string;
    readonly properties?: ReadonlyArray<Record<string, unknown>>;
    readonly groups?: ReadonlyArray<Record<string, unknown>>;
}

export declare const EventStatus: {
    readonly IN_PROGRESS: "IN_PROGRESS";
    readonly COMPLETED: "COMPLETED";
    readonly ERROR: "ERROR";
};

export declare type EventStatusValue = (typeof EventStatus)[keyof typeof EventStatus];

export declare const EventType: {
    readonly THINKING: "THINKING";
    readonly EXECUTION_PLAN: "EXECUTION_PLAN";
    readonly TOOL_PLANNING: "TOOL_PLANNING";
    readonly TOOL_INVOKE: "TOOL_INVOKE";
    readonly EXTERNAL_TOOL_CALL: "EXTERNAL_TOOL_CALL";
    readonly TOOL_RESULT: "TOOL_RESULT";
    readonly EXECUTION_COMPLETE: "EXECUTION_COMPLETE";
    readonly ERROR: "ERROR";
    readonly CONVERSATION_TITLE: "CONVERSATION_TITLE";
};

export declare type EventTypeValue = (typeof EventType)[keyof typeof EventType];

declare interface FeelValidationResult {
    readonly error?: string;
    readonly warnings?: ReadonlyArray<string>;
}

export declare interface FileInfo {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly lastModified?: string;
}

export declare interface FormLintError {
    readonly fieldId: string;
    readonly message: string;
    readonly category: string;
}

export declare const getDefaultStatusLabel: (eventType: EventTypeValue, toolName?: string, eventStatus?: EventStatusValue) => string;

export declare interface ImportResult {
    readonly error?: unknown;
}

export declare const isApplyElementTemplateCompleted: (event: AgentEvent) => boolean;

export declare const isCurrentFileModified: (event: AgentEvent) => boolean;

export declare const isLayoutBpmnXmlCompleted: (event: AgentEvent) => boolean;

export declare const isToolAllowedWithCapabilities: (tool: {
    readonly requiredCapabilities?: ReadonlyArray<string>;
}, capabilities: ReadonlyArray<string>) => boolean;

export declare const isWriteArtifactCompleted: (event: AgentEvent) => boolean;

declare interface KapaAiSearchResult {
    readonly content: string;
    readonly source_url: string;
    readonly title: string;
    readonly source_type: string;
}

declare interface KapaToolResult {
    readonly sources: readonly KapaAiSearchResult[];
}

export { layoutProcess }

export declare interface LintError {
    readonly elementId: string;
    readonly elementName?: string;
    readonly message: string;
    readonly category: string;
    readonly rule: string;
    readonly documentationUrl?: string;
}

export declare const MarkdownRenderer: ({ content, streaming, highlight, ...rest }: MarkdownRendererProps) => JSX.Element | null;

declare type MarkdownRendererProps = Omit<MarkdownProps, 'children'> & {
    readonly content: string;
};

declare type MessageErrorCallback = (messageId: string, errorMessage: string) => void;

export declare type MessageRole = 'user' | 'assistant';

export declare type MessageStatus = 'pending' | 'streaming' | 'complete' | 'error';

declare type MessageStatusCallback = (messageId: string, status: 'complete' | 'error') => void;

declare type MixedToolHandler = (toolResult: string, onError: (toolName: string, error: Error) => void) => KapaToolResult | null;

declare interface MixedToolResultEvent {
    readonly type: typeof ResultType.MIXED_TOOL_RESULT;
    readonly toolName: string;
    readonly toolResult: string;
}

export declare interface ModelerAccess {
    readonly getModeler: () => BpmnModeler;
    readonly getCanvas: () => unknown;
    readonly getElementRegistry: () => {
        readonly get: (id: string) => BpmnElement | undefined;
        readonly getAll: () => ReadonlyArray<BpmnElement>;
        readonly filter: (predicate: (el: BpmnElement) => boolean) => ReadonlyArray<BpmnElement>;
    };
    readonly getModeling: () => unknown;
    readonly getCommandStack: () => unknown;
    readonly getElementFactory: () => unknown;
    readonly getModdle: () => unknown;
    readonly getSelection: () => unknown;
    readonly getElementById: (id: string) => BpmnElement;
    readonly is: (element: unknown, type: string) => boolean;
    readonly getBusinessObject: (element: unknown) => Record<string, unknown>;
    readonly getBpmnRules?: () => unknown;
}

declare interface ModelerAccessOptions {
    readonly getModeler: () => BpmnModeler;
    readonly is: (element: unknown, type: string) => boolean;
    readonly getBusinessObject: (element: unknown) => Record<string, unknown>;
}

export declare const openSidecar: () => void;

export declare interface ProjectContext {
    readonly id: string;
    readonly folderId: string | null;
}

export declare class QueryCancelledError extends Error {
    constructor(message?: string);
}

export declare interface RawBpmnLintError {
    readonly id: string;
    readonly name?: string;
    readonly message: string;
    readonly category: string;
    readonly rule: string;
    readonly meta?: {
        readonly documentation?: {
            readonly url?: string;
        };
    };
}

export declare const ResultType: {
    readonly TOOL_INVOKE: "TOOL_INVOKE";
    readonly MIXED_TOOL_RESULT: "MIXED_TOOL_RESULT";
    readonly CONVERSATION_TITLE: "CONVERSATION_TITLE";
};

export declare type ResultTypeValue = (typeof ResultType)[keyof typeof ResultType];

export declare interface RollbackResponse {
    readonly milestoneId: string;
    readonly message: string;
}

export declare const selectAgentState: (state: AgentStoreState) => string;

export declare const selectIsBusy: (state: AgentStoreState) => boolean;

export declare interface SendMessagePayload {
    readonly conversationId: string;
    readonly messageId: string;
    readonly content: string;
    readonly externalTools: readonly BackendToolPayload[];
    readonly context: Record<string, unknown>;
}

export declare const setupBpmnTools: (context: CopilotToolContext, registry: ToolRegistry) => void;

export declare interface SidecarRenderProps {
    readonly onMinimize: () => void;
    readonly isOpen: boolean;
}

declare interface SourceReference {
    readonly content: string;
    readonly sourceUrl: string;
    readonly title: string;
    readonly sourceType: string;
}

declare type StatusLabelCallback = (messageId: string, label: string) => void;

export declare interface Suggestion {
    readonly label: string;
}

declare interface ThinkingBlock {
    readonly id: string;
    readonly content: string;
    readonly label: string;
    readonly isComplete: boolean;
    readonly kind?: ThinkingBlockKind;
    readonly toolName?: string;
    readonly toolStatus?: ToolBlockStatus;
    readonly stepNumber?: number;
    readonly toolArguments?: Record<string, unknown>;
    readonly toolResult?: string;
    readonly description?: string;
}

declare type ThinkingBlockKind = 'reasoning' | 'tool' | 'error';

export declare const ThinkingIndicator: ({ label, completedLabel, content, isComplete, }: ThinkingIndicatorProps) => JSX.Element | null;

declare interface ThinkingIndicatorProps {
    readonly label?: string;
    readonly completedLabel?: string;
    readonly content?: string;
    readonly isComplete: boolean;
}

export declare const toggleSidecar: () => void;

declare type ToolBlockStatus = 'success' | 'processing' | 'error';

export declare type ToolCategory = 'BPMN_CORE' | 'BPMN_MODIFICATION' | 'BPMN_QUERY' | 'DMN_QUERY' | 'DMN_MODIFICATION' | 'FEEL_CORE' | 'FORM_CORE' | 'INTEGRATION' | 'FILE_OPERATION' | 'GENERAL';

export declare interface ToolDefinition {
    readonly name: string;
    readonly description: string;
    readonly parameters: Record<string, unknown>;
    readonly type: ToolType;
    readonly contentType: ContentType;
    readonly category: ToolCategory;
    readonly requiredCapabilities?: ReadonlyArray<string>;
    readonly displayName?: string;
    readonly descriptionFormatter?: DescriptionFormatter;
}

export declare type ToolHandler = (args: Record<string, unknown>, onError: (toolName: string, error: Error) => void) => Promise<unknown>;

declare type ToolHandler_2 = (args: Record<string, unknown>, onError: (toolName: string, error: Error) => void) => Promise<unknown>;

export declare interface ToolInvokeResult {
    readonly type: typeof ResultType.TOOL_INVOKE;
    readonly toolName: string;
    readonly toolCallId?: string;
    readonly toolArguments: Record<string, unknown>;
}

export declare interface ToolRegistration extends ToolDefinition {
    readonly handler: ToolHandler | MixedToolHandler;
}

declare interface ToolRegistration_2 extends ToolDefinition {
    readonly handler: ToolHandler_2;
}

export declare interface ToolRegistry {
    readonly registerTool: (registration: ToolRegistration_2) => void;
    readonly clear: () => void;
}

export declare const toolRegistry: {
    registerTool: (registration: ToolRegistration) => void;
    has: (name: string) => boolean;
    hasHandler: (name: string) => boolean;
    isMixed: (name: string) => boolean;
    getDisplayName: (name: string) => string | undefined;
    getDescription: (name: string, args: Record<string, unknown>) => string;
    hasCustomDescription: (name: string) => boolean;
    getContentType: (name: string) => ContentType;
    getMixedHandler: (name: string) => MixedToolHandler | undefined;
    getHandler: (name: string) => ToolHandler | undefined;
    executeHandler: (name: string, args: Record<string, unknown>, onError: (toolName: string, error: Error) => void) => Promise<unknown>;
    getToolsForBackend: () => readonly BackendToolPayload[];
    clear: () => void;
};

export declare interface ToolResultPayload {
    readonly conversationId: string;
    readonly type: 'TOOL_RESULT';
    readonly toolName: string;
    readonly toolCallId?: string;
    readonly toolResult: string;
    readonly contentType: 'TEXT' | 'BPMN' | 'JSON';
}

export declare type ToolType = 'frontend' | 'mixed';

export declare const traverseModdle: (root: DmnModdleElement | Record<string, unknown>, visitor: TraverseVisitor) => void;

declare type TraverseVisitor = (element: DmnModdleElement) => void | false;

declare interface UpdateConversationPayload {
    readonly conversationName: string;
}

export declare const useAgentAdapter: ({ transport, onToolInvoke, getStatusLabel, }: AgentAdapterConfig) => AgentAdapterReturn;

export declare const useAgentStore: UseBoundStore<StoreApi<AgentStore>>;

export declare const useChatStore: UseBoundStore<StoreApi<ChatStore>>;

export declare const useSidecarOpen: () => boolean;

export { }
