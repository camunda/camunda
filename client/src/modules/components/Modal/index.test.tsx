/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Modal from './index';

const HeaderContent = () => <div>Header Content</div>;
const BodyContent = () => <div>Body Content</div>;
const onModalClose = jest.fn();
const primaryButtonClickHandler = jest.fn();

const ModalComponent = (
  <Modal
    onModalClose={onModalClose}
    isVisible={true}
    className="modal-root"
    size="BIG"
  >
    <Modal.Header>
      <HeaderContent />
    </Modal.Header>
    <Modal.Body>
      <BodyContent />
    </Modal.Body>
    <Modal.Footer>
      <Modal.PrimaryButton onClick={primaryButtonClickHandler}>
        Some Primary Button
      </Modal.PrimaryButton>
    </Modal.Footer>
  </Modal>
);

describe('Modal', () => {
  it('should render modal in a div in the document body', () => {
    render(ModalComponent, {wrapper: ThemeProvider});

    expect(screen.getByText('Header Content')).toBeInTheDocument();
    expect(screen.getByText('Body Content')).toBeInTheDocument();
    expect(screen.getByTestId('cross-button')).toBeInTheDocument();
    expect(screen.getByText('Some Primary Button')).toBeInTheDocument();
  });

  it('should call on modal close', async () => {
    const {user} = render(ModalComponent, {wrapper: ThemeProvider});

    await user.click(screen.getByTestId('cross-button'));

    expect(onModalClose).toBeCalled();
  });

  it('should respond to key presses', async () => {
    const {user} = render(ModalComponent, {wrapper: ThemeProvider});

    await user.keyboard('{Escape}');
    expect(onModalClose).toBeCalled();

    await user.keyboard('{Enter}');
    expect(primaryButtonClickHandler).toBeCalled();
  });
});
