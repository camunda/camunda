/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Modal, Switch} from '@carbon/react';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {JsonModalToolbar, ModalContent, ModalToolbar, ViewSwitcher} from './conversation.styled';
import {MarkdownMessage} from './MarkdownMessage';

type ModalView = 'preview' | 'source';

type MessageDetailsModalProps = {
	title: string;
	language: 'markdown' | 'json';
	content: string;
	onClose: () => void;
};

const MessageDetailsModal: React.FC<MessageDetailsModalProps> = ({title, language, content, onClose}) => {
	const {t} = useTranslation();
	const [modalView, setModalView] = useState<ModalView>('preview');

	return (
		<Modal
			open
			modalHeading={title}
			onRequestClose={onClose}
			closeButtonLabel={t('operate.shared.editors.close')}
			size="lg"
			passiveModal
		>
			{language === 'markdown' ? (
				<>
					<ModalToolbar>
						<ViewSwitcher
							size="sm"
							onChange={({name}) => setModalView(name === 'source' ? 'source' : 'preview')}
							selectedIndex={modalView === 'preview' ? 0 : 1}
						>
							<Switch name="preview" text={t('operate.processInstance.details.agent.preview')} />
							<Switch name="source" text={t('operate.processInstance.details.agent.source')} />
						</ViewSwitcher>
						<CopyButton value={content} />
					</ModalToolbar>
					{modalView === 'preview' ? (
						<ModalContent>
							<MarkdownMessage content={content} />
						</ModalContent>
					) : (
						<RichTextEditor value={content} readOnly language="markdown" />
					)}
				</>
			) : (
				<>
					<JsonModalToolbar>
						<CopyButton value={content} />
					</JsonModalToolbar>
					<RichTextEditor value={content} readOnly language={language} />
				</>
			)}
		</Modal>
	);
};

export {MessageDetailsModal};
