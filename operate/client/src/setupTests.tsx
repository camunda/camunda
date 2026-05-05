/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@testing-library/jest-dom';
import {mockServer} from 'modules/mock-server/node';
import {configure} from 'modules/testing-library';
import MockDmnJsSharedManager from '__mocks__/dmn-js-shared/lib/base/Manager';
import MockDmnJsSharedDiUtil from '__mocks__/dmn-js-shared/lib/util/DiUtil';
import MockDmnJsSharedModelUtil from '__mocks__/dmn-js-shared/lib/util/ModelUtil';
import MockDmnJsDecisionTableViewer from '__mocks__/dmn-js-decision-table/lib/Viewer';
import MockDmnJsDrdNavigatedViewer from '__mocks__/dmn-js-drd/lib/NavigatedViewer';
import MockDmnJsLiteralExpressionViewer from '__mocks__/dmn-js-literal-expression/lib/Viewer';
import MockSplitter, {SplitDirection} from './__mocks__/@devbookhq/splitter';
import MockBpmnJsNavigatedViewer from '__mocks__/bpmn-js/lib/NavigatedViewer';
import MockBpmnJs from '__mocks__/bpmn-js';
import MockBpmnIoElementTemplateIconRenderer from '__mocks__/@bpmn-io/element-template-icon-renderer';
import MockReactMarkdown from '__mocks__/react-markdown';
import ResizeObserverPolyfill from 'resize-observer-polyfill';
import {elementInstancesTreeStore} from 'App/ProcessInstance/ElementInstanceLog/ElementInstancesTree/elementInstancesTreeStore';

vi.mock('dmn-js-shared/lib/base/Manager', () => ({
  default: MockDmnJsSharedManager,
}));
vi.mock('dmn-js-shared/lib/util/DiUtil', () => ({
  default: MockDmnJsSharedDiUtil,
}));
vi.mock('dmn-js-shared/lib/util/ModelUtil', () => ({
  default: MockDmnJsSharedModelUtil,
}));
vi.mock('dmn-js-decision-table/lib/Viewer', () => ({
  default: MockDmnJsDecisionTableViewer,
}));
vi.mock('dmn-js-drd/lib/NavigatedViewer', () => ({
  default: MockDmnJsDrdNavigatedViewer,
}));
vi.mock('dmn-js-literal-expression/lib/Viewer', () => ({
  default: MockDmnJsLiteralExpressionViewer,
}));

vi.mock('modules/components/InlineJsonEditor', async () => {
  const {useFieldError} = await import('modules/hooks/useFieldError');

  const FieldErrorMessage: React.FC<{name: string}> = ({name}) => {
    const fieldError = useFieldError(name);
    if (!fieldError) {
      return null;
    }
    return <div className="cds--form-requirement">{fieldError}</div>;
  };

  const InlineJsonEditorBase = ({
    value,
    onChange,
    'data-testid': dataTestId,
    readOnly,
    onBlur,
    onFocus,
    id,
    name,
  }: {
    value: string;
    onChange?: (value: string) => void;
    'data-testid'?: string;
    readOnly?: boolean;
    onBlur?: () => void;
    onFocus?: () => void;
    id?: string;
    name?: string;
  }) => {
    if (readOnly) {
      return (
        <span data-testid={dataTestId} id={id}>
          {value ?? ''}
        </span>
      );
    }
    return (
      <>
        {id && (
          <label htmlFor={id} className="cds--visually-hidden">
            Value
          </label>
        )}
        <input
          type="text"
          data-testid={dataTestId}
          id={id}
          value={value ?? ''}
          onChange={(event) => onChange?.(event.target.value)}
          onBlur={onBlur}
          onFocus={onFocus}
        />
        {name && <FieldErrorMessage name={name} />}
      </>
    );
  };

  return {InlineJsonEditor: InlineJsonEditorBase};
});

vi.mock('modules/components/JSONEditor', () => {
  return {
    useMonaco: () => {},
    JSONEditor: ({
      value,
      onChange,
    }: {
      value: string;
      onChange?: (value: string) => void;
    }) => (
      <textarea
        data-testid="monaco-editor"
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
      />
    ),
  };
});

vi.mock('modules/components/DiffEditor', () => {
  return {
    useMonaco: () => {},
    DiffEditor: () => <textarea data-testid="monaco-diff-editor" />,
  };
});

vi.mock('modules/loadMonaco', () => ({
  loadMonaco: () => {},
}));

vi.mock('@devbookhq/splitter', async () => {
  const actual = await vi.importActual('@devbookhq/splitter');
  return {
    ...actual,
    default: MockSplitter,
    SplitDirection: SplitDirection,
  };
});

vi.mock('modules/components/InfiniteScroller', () => {
  const InfiniteScroller: React.FC<{children?: React.ReactNode}> = ({
    children,
  }) => {
    return <>{children}</>;
  };
  return {InfiniteScroller};
});

vi.mock('modules/stores/licenseTag', () => ({
  licenseTagStore: {
    fetchLicense: vi.fn(),
    state: {isTagVisible: false},
  },
}));

vi.mock('bpmn-js/lib/features/outline', () => ({default: () => {}}));

vi.mock('diagram-js-minimap', () => ({default: () => {}}));

vi.mock('@floating-ui/react-dom', async () => {
  const originalModule = await vi.importActual('@floating-ui/react-dom');

  return {
    ...originalModule,
    hide: () => {},
  };
});

vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: MockBpmnJsNavigatedViewer,
}));

vi.mock('bpmn-js', () => MockBpmnJs);

vi.mock('@bpmn-io/element-template-icon-renderer', () => ({
  default: MockBpmnIoElementTemplateIconRenderer,
}));

vi.mock('react-markdown', () => ({default: MockReactMarkdown}));

const localStorageMock = (function () {
  let store: {[key: string]: string} = {};
  return {
    getItem(key: string) {
      return store[key];
    },
    setItem(key: string, value: string) {
      store[key] = value;
    },
    clear() {
      store = {};
    },
    removeItem: vi.fn(),
  };
})();

beforeEach(() => (elementInstancesTreeStore.forceDisablePolling = true));
afterEach(() => (elementInstancesTreeStore.forceDisablePolling = false));

let unhandledRequestsCount = 0;
beforeAll(() => {
  mockServer.listen({
    onUnhandledRequest: (_, print) => {
      unhandledRequestsCount++;
      return print.error();
    },
  });

  // temporary fix while jsdom doesn't implement this: https://github.com/jsdom/jsdom/issues/1695
  window.HTMLElement.prototype.scrollIntoView = function () {};

  // jsdom doesn't implement window.prompt, needed by copy-to-clipboard (used by Carbon CodeSnippet)
  window.prompt = vi.fn();
});
afterEach(() => {
  mockServer.resetHandlers();
  if (unhandledRequestsCount !== 0) {
    const reportCount = unhandledRequestsCount;
    unhandledRequestsCount = 0;
    throw new Error(
      `[Unhandled Requests] The test produced ${reportCount} unhandled request(s).`,
    );
  }
});
afterAll(() => mockServer.close());
beforeEach(async () => {
  vi.stubEnv('TZ', 'UTC');
  vi.stubGlobal('ResizeObserver', ResizeObserverPolyfill);
  vi.stubGlobal('localStorage', localStorageMock);
  vi.stubGlobal('MutationObserver', MutationObserver);
  vi.stubGlobal('clientConfig', {
    canLogout: true,
  });
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );

  localStorage.clear();
});

configure({
  asyncUtilTimeout: 7000,
});
