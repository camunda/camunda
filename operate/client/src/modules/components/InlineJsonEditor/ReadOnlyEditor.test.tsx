/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ReadOnlyEditor} from './ReadOnlyEditor';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockWriteText = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  mockWriteText.mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', {
    value: {writeText: mockWriteText},
    configurable: true,
    writable: true,
  });
});

afterEach(() => {
  Object.defineProperty(navigator, 'clipboard', {
    value: undefined,
    configurable: true,
    writable: true,
  });
});

const defaultProps = {
  value: '"hello"',
  placeholder: 'Value',
  isReadOnly: true,
  height: 32,
  label: 'myVar',
} as const;

describe('<ReadOnlyEditor />', () => {
  describe('copy to clipboard', () => {
    it('should copy the value and show a success notification on click', async () => {
      const {user} = render(<ReadOnlyEditor {...defaultProps} />);

      await user.click(screen.getByTestId('json-editor-readonly'));

      expect(mockWriteText).toHaveBeenCalledWith('"hello"');
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'myVar copied to clipboard',
        isDismissable: true,
      });
    });

    it('should show an error notification when clipboard write fails', async () => {
      mockWriteText.mockRejectedValue(new Error('Permission denied'));
      const {user} = render(<ReadOnlyEditor {...defaultProps} />);

      await user.click(screen.getByTestId('json-editor-readonly'));

      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to copy myVar to clipboard',
        isDismissable: true,
      });
    });

    it('should not have role button when not in read-only mode', () => {
      render(<ReadOnlyEditor {...defaultProps} isReadOnly={false} />);

      expect(
        screen.queryByRole('button', {name: 'Copy myVar'}),
      ).not.toBeInTheDocument();
    });

    it('should show copy icon indicator in read-only mode', () => {
      render(<ReadOnlyEditor {...defaultProps} />);

      expect(screen.getByTestId('copy-icon-indicator')).toBeInTheDocument();
    });

    it('should not show copy icon indicator when not in read-only mode', () => {
      render(<ReadOnlyEditor {...defaultProps} isReadOnly={false} />);

      expect(
        screen.queryByTestId('copy-icon-indicator'),
      ).not.toBeInTheDocument();
    });
  });

  describe('onCopy prop (truncated value handling)', () => {
    it('should call onCopy to get the value and copy the result when onCopy is provided', async () => {
      const onCopy = vi.fn().mockResolvedValue('"full value"');
      const {user} = render(
        <ReadOnlyEditor
          {...defaultProps}
          value='"truncated..."'
          onCopy={onCopy}
        />,
      );

      await user.click(screen.getByTestId('json-editor-readonly'));

      expect(onCopy).toHaveBeenCalled();
      expect(mockWriteText).toHaveBeenCalledWith('"full value"');
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'myVar copied to clipboard',
        isDismissable: true,
      });
    });

    it('should not copy the truncated value when onCopy is provided', async () => {
      const onCopy = vi.fn().mockResolvedValue('"full value"');
      const {user} = render(
        <ReadOnlyEditor
          {...defaultProps}
          value='"truncated..."'
          onCopy={onCopy}
        />,
      );

      await user.click(screen.getByTestId('json-editor-readonly'));

      expect(mockWriteText).not.toHaveBeenCalledWith('"truncated..."');
    });

    it('should show an error notification and not copy when onCopy rejects', async () => {
      const onCopy = vi.fn().mockRejectedValue(new Error('Network error'));
      const {user} = render(
        <ReadOnlyEditor {...defaultProps} onCopy={onCopy} />,
      );

      await user.click(screen.getByTestId('json-editor-readonly'));

      expect(mockWriteText).not.toHaveBeenCalled();
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to fetch full myVar',
        isDismissable: true,
      });
    });

    it('should show the loading spinner while onCopy is in progress', async () => {
      let resolveOnCopy!: (value: string) => void;
      const onCopy = vi.fn(
        () =>
          new Promise<string>((resolve) => {
            resolveOnCopy = resolve;
          }),
      );
      const {user} = render(
        <ReadOnlyEditor {...defaultProps} onCopy={onCopy} />,
      );

      user.click(screen.getByTestId('json-editor-readonly'));

      expect(
        await screen.findByTestId('copy-loading-indicator'),
      ).toBeInTheDocument();
      expect(
        screen.queryByTestId('copy-icon-indicator'),
      ).not.toBeInTheDocument();

      resolveOnCopy('"full value"');

      expect(
        await screen.findByTestId('copy-icon-indicator'),
      ).toBeInTheDocument();
      expect(
        screen.queryByTestId('copy-loading-indicator'),
      ).not.toBeInTheDocument();
    });
  });

  describe('renderButton prop', () => {
    it('should render a custom button when renderButton is provided', () => {
      render(
        <ReadOnlyEditor
          {...defaultProps}
          renderButton={() => <button>Show all</button>}
        />,
      );

      expect(
        screen.getByRole('button', {name: 'Show all'}),
      ).toBeInTheDocument();
    });

    it('should not render any extra button when renderButton is not provided', () => {
      render(<ReadOnlyEditor {...defaultProps} />);

      // Only the copy button exists
      expect(screen.getAllByRole('button')).toHaveLength(1);
    });
  });
});
