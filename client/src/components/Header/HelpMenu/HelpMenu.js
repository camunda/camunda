/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import ReactMarkdown from 'react-markdown';

import {Button, Modal, LoadingIndicator, Dropdown, Icon} from 'components';
import {withDocs, withErrorHandling} from 'HOC';
import {t, getLanguage} from 'translation';
import {showError} from 'notifications';
import {getOptimizeVersion} from 'config';

import {isChangeLogSeen, setChangeLogAsSeen, getMarkdownText} from './service';

import './HelpMenu.scss';

export class HelpMenu extends React.Component {
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
      this.props.mightFail(getMarkdownText(localCode), (text) => this.setState({text}), showError);
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
      <div className="HelpMenu">
        <Dropdown
          className="helpDropdown"
          label={
            <>
              <Icon type="question-mark" />
              {t('navigation.help')}
            </>
          }
        >
          <Dropdown.Option onClick={() => this.setState({open: true})}>
            {t('whatsNew.buttonTitle')}
          </Dropdown.Option>
          <Dropdown.Option
            onClick={() => {
              window.open(this.props.docsLink + '/user-guide', '_blank', 'noopener,noreferrer');
            }}
          >
            {t('navigation.userGuide')}
          </Dropdown.Option>
        </Dropdown>
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

export default withDocs(withErrorHandling(HelpMenu));
