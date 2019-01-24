import React from 'react';
import PropTypes from 'prop-types';

import {compactObject, pickFromObject} from 'modules/utils';
import Modal from 'modules/components/Modal';
import Button from 'modules/components/Button';

import Overlay from '../Overlay';
import * as Styled from './styled';

const position = {
  bottom: -12,
  left: -20
};

export default class PopoverOverlay extends React.Component {
  state = {
    isModalVisibile: false
  };

  handleModalClose = () => this.setState({isModalVisibile: false});

  render() {
    const {
      metadata,
      onOverlayAdd,
      onOverlayClear,
      isViewerLoaded,
      selectedFlowNode,
      theme
    } = this.props;

    const summaryKeys = [
      'Flow Node Instance Id',
      'Job Id',
      'Started',
      'Completed'
    ];
    const summary = compactObject(pickFromObject(metadata, summaryKeys));

    return (
      <Overlay
        onOverlayAdd={onOverlayAdd}
        onOverlayClear={onOverlayClear}
        isViewerLoaded={isViewerLoaded}
        id={selectedFlowNode.id}
        type={'popover'}
        position={position}
      >
        <Styled.Popover theme={theme}>
          <Styled.Metadata>
            <tbody>
              {Object.entries(summary).map(([key, value]) => {
                return (
                  <Styled.MetadataRow key={key}>
                    <td>{key}:</td>
                    <td>{JSON.stringify(value)}</td>
                  </Styled.MetadataRow>
                );
              })}
            </tbody>
          </Styled.Metadata>
          <Styled.MoreButton
            onClick={() => this.setState({isModalVisibile: true})}
            title="Show more metadata"
            data-test="more-metadata"
          >
            More...
          </Styled.MoreButton>
          {this.state.isModalVisibile && (
            <Modal onModalClose={this.handleModalClose}>
              <Modal.Header>{`Flow Node Instance "${selectedFlowNode.name ||
                metadata['Flow Node Instance Id']}" Metadata`}</Modal.Header>
              <Styled.ModalBody>
                <pre>
                  <Styled.LinesSeparator />
                  <code className="language-json">
                    {JSON.stringify(metadata, null, '\t')
                      .split('\n')
                      .map((line, idx) => (
                        <Styled.CodeLine key={idx}>{line}</Styled.CodeLine>
                      ))}
                  </code>
                </pre>
              </Styled.ModalBody>
              <Modal.Footer>
                <Button
                  color="primary"
                  size="medium"
                  onClick={this.handleModalClose}
                  title="close the metadata modal"
                >
                  Close
                </Button>
              </Modal.Footer>
            </Modal>
          )}
        </Styled.Popover>
      </Overlay>
    );
  }
}

PopoverOverlay.propTypes = {
  metadata: PropTypes.object,
  selectedFlowNode: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired
  }),
  onOverlayAdd: PropTypes.func.isRequired,
  onOverlayClear: PropTypes.func.isRequired,
  isViewerLoaded: PropTypes.bool.isRequired,
  theme: PropTypes.oneOf(['dark', 'light']).isRequired
};
