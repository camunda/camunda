/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Dialog,
	DialogBody,
	DialogContent,
	DialogHeader,
	DialogTitle,
	Tabs,
	TabsContent,
	TabsList,
	TabsTrigger,
} from '@camunda/design-system';
import {CopyButton} from '#/operate/shared/CopyButton/shadcn.components/CopyButton';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
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
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="lg" closeLabel={t('operate.shared.editors.close')} aria-describedby={undefined}>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					{language === 'markdown' ? (
						<Tabs value={modalView} onValueChange={(value) => setModalView(value === 'source' ? 'source' : 'preview')}>
							<div className="mb-5 flex items-center justify-between">
								<TabsList>
									<TabsTrigger value="preview">{t('operate.processInstance.details.agent.preview')}</TabsTrigger>
									<TabsTrigger value="source">{t('operate.processInstance.details.agent.source')}</TabsTrigger>
								</TabsList>
								<CopyButton value={content} />
							</div>
							<TabsContent value="preview" className="min-h-[60vh] max-h-[70vh] overflow-y-auto">
								<MarkdownMessage content={content} />
							</TabsContent>
							<TabsContent value="source">
								<RichTextEditor value={content} readOnly language="markdown" />
							</TabsContent>
						</Tabs>
					) : (
						<>
							<div className="mb-5 flex justify-end">
								<CopyButton value={content} />
							</div>
							<RichTextEditor value={content} readOnly language={language} />
						</>
					)}
				</DialogBody>
			</DialogContent>
		</Dialog>
	);
};

export {MessageDetailsModal};
