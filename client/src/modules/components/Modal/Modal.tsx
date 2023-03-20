/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Component,
  createContext,
  createRef,
  KeyboardEventHandler,
  ReactNode,
  RefObject,
} from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';

import './Modal.scss';

type ModalContextValue = {
  onClose?: () => void;
};

const ModalContext = createContext<ModalContextValue>({});

type ModalProps = {
  onClose?: () => void;
  noAutoFocus?: boolean;
  open?: boolean;
  className?: string;
  children?: ReactNode;
  onConfirm?: KeyboardEventHandler<HTMLElement>;
  size?: 'large' | 'max';
};

export default class Modal extends Component<ModalProps> {
  el: HTMLDivElement;
  focusTrap: RefObject<HTMLDivElement>;
  container: RefObject<HTMLDivElement>;
  modalContext: ModalContextValue;

  constructor(props: ModalProps) {
    super(props);
    this.el = document.createElement('div');
    this.focusTrap = createRef<HTMLDivElement>();
    this.container = createRef<HTMLDivElement>();
    this.modalContext = {
      onClose: () => {
        this.props.onClose?.();
      },
    };
  }

  componentDidMount() {
    document.body.appendChild(this.el);
    this.fixPositioning();
    this.setFocus();
    new MutationObserver(this.fixPositioning).observe(this.el, {childList: true, subtree: true});
    window.addEventListener('resize', this.fixPositioning);
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    window.removeEventListener('resize', this.fixPositioning);
  }

  fixPositioning = () => {
    const container = this.container.current;
    if (container) {
      const windowHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);
      const margin = 30; // already set top  (15 px) + bottom  (15 px) margin
      let topMargin = (windowHeight - container.clientHeight) / 2 - margin;
      topMargin = Math.max(topMargin, 0);
      container.style.marginTop = topMargin + 'px';
      container.style.marginLeft = -container.clientWidth / 2 + 'px';
    }
  };

  setFocus = () => {
    const container = this.container.current;
    if (container && this.props.open) {
      if (this.props.noAutoFocus) {
        return container.focus();
      }

      // focus the default element in the modal, in order:
      // - the first text input
      // - the first primary button
      // - any focusable element
      const defaultElement: HTMLElement | null =
        container.querySelector('input[type="text"]') ||
        container.querySelector('.primary') ||
        container.querySelector('input, button, textarea, select');
      if (defaultElement) {
        if (defaultElement.className.includes('typeaheadInput')) {
          container.querySelector<HTMLElement>('.optionsButton')?.focus();
        } else {
          defaultElement.focus();
        }
      }

      // safeguard in case there is no focusable element or the default element is disabled
      if (!container.contains(document.activeElement)) {
        container.focus();
      }
    }
  };

  handleKeyPress: KeyboardEventHandler<HTMLElement> = (evt) => {
    const eventTarget = evt.target as HTMLElement | HTMLInputElement;
    if (evt.key === 'Escape') {
      const handler = this.props.onClose;
      handler && handler();
    } else if (
      evt.key === 'Enter' &&
      this.props.open &&
      eventTarget.tagName.toLowerCase() !== 'button' &&
      !('type' in eventTarget && ['checkbox', 'radio'].includes(eventTarget.type))
    ) {
      const handler = this.props.onConfirm;
      handler && handler(evt);
    }
  };

  render() {
    const {open, children} = this.props;

    if (open) {
      return ReactDOM.createPortal(
        <ModalContext.Provider value={this.modalContext}>
          <div
            className="Modal"
            role="dialog"
            onClick={(evt) => evt.stopPropagation()}
            onMouseDown={(evt) => evt.stopPropagation()}
            onKeyDown={this.handleKeyPress}
          >
            <div className="Modal__scroll-container">
              <div tabIndex={0} onFocus={() => this.focusTrap.current?.focus()} />
              <div
                className={classnames('Modal__content-container', this.props.className, {
                  ['Modal__content-container--' + this.props.size]: this.props.size,
                })}
                tabIndex={-1}
                ref={this.container}
              >
                {children}
              </div>
              <div tabIndex={-1} ref={this.focusTrap} />
              <div tabIndex={0} onFocus={() => this.container.current?.focus()} />
            </div>
          </div>
        </ModalContext.Provider>,
        this.el
      );
    }

    return null;
  }

  componentDidUpdate(prevProps: ModalProps) {
    if (!prevProps.open && this.props.open) {
      this.setFocus();
    }

    this.fixPositioning();
  }

  static Header = function ({children}: {children: ReactNode}) {
    return (
      <ModalContext.Consumer>
        {({onClose}) => (
          <div className="Modal__header">
            <h1 className="Modal__heading">{children}</h1>
            <button className="Modal__close-button" onClick={onClose} />
          </div>
        )}
      </ModalContext.Consumer>
    );
  };

  static Content = function ({className, children}: {className?: string; children: ReactNode}) {
    return <div className={classnames('Modal__content', className)}>{children}</div>;
  };

  static Actions = function ({children}: {children: ReactNode}) {
    return <div className="Modal__actions">{children}</div>;
  };
}
