/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Modal, LoadingIndicator} from 'components';
import {t, getLanguage} from 'translation';
import ReactMarkdown from 'react-markdown';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {getOptimizeVersion} from 'config';
import {isChangeLogSeen, setChangeLogAsSeen, getMarkdownText} from './service';
import './ChangeLog.scss';

export default withErrorHandling(
  class ChangeLog extends React.Component {
    state = {
      open: false,
      text: '',
      optimizeVersion: null,
      seen: true,
    };

    componentDidUpdate(_, prevState) {
      const {open, seen, text} = this.state;
      const openedOrFirstSeen = (!prevState.open && open) || (prevState.seen && !seen);
      if (openedOrFirstSeen && !text) {
        const localCode = getLanguage();
        this.props.mightFail(
          getMarkdownText(localCode),
          (text) => this.setState({text}),
          showError
        );
      }
    }

    async componentDidMount() {
      this.props.mightFail(isChangeLogSeen(), ({seen}) => this.setState({seen}), showError);

      this.setState({
        optimizeVersion: await getOptimizeVersion(),
      });
    }

    closeModal = () => {
      this.setState({open: false});
      if (!this.state.seen) {
        this.setState({seen: true});
        setChangeLogAsSeen();
      }
    };

    render() {
      const {open, optimizeVersion, text, seen} = this.state;

      return (
        <div className="ChangeLog">
          <Button link onClick={() => this.setState({open: true})}>
            {t('whatsNew.buttonTitle')}
          </Button>
          <Modal className="ChangeLogModal" open={open || !seen} onClose={this.closeModal}>
            <Modal.Header>
              {t('whatsNew.modalHeader')} {optimizeVersion}
            </Modal.Header>
            <Modal.Content>
              {text ? <ReactMarkdown>{text}</ReactMarkdown> : <LoadingIndicator />}
            </Modal.Content>
            <Modal.Actions>
              <Button main className="close" onClick={this.closeModal}>
                {t('common.close')}
              </Button>
            </Modal.Actions>
          </Modal>
        </div>
      );
    }
  }
);
