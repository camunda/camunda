/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Text} from '@camunda/design-system';
import {Upload} from '@camunda/design-system/icons';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {LogoUploadDialog} from './LogoUploadDialog';

type Props = {
	onChange: (logo: string) => void;
};

const LogoField: React.FC<Props> = ({onChange}) => {
	const {t} = useTranslation();
	const [isDialogOpen, setIsDialogOpen] = useState(false);

	return (
		<div className="flex flex-col gap-2">
			<Button
				type="button"
				variant="secondary"
				className="h-auto w-full justify-between gap-3 px-3 py-2 text-left"
				onClick={() => setIsDialogOpen(true)}
			>
				<span className="flex min-w-0 flex-col">
					<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
						{t('tasklist.themeEditorLogo')}
					</Text>
					<Text as="span" variant="label-md-strong" className="truncate">
						{t('tasklist.themeEditorLogoUpload')}
					</Text>
				</span>
				<Upload aria-hidden className="size-4 shrink-0 text-neutral-foreground-subtle" />
			</Button>
			<LogoUploadDialog isOpen={isDialogOpen} onOpenChange={setIsDialogOpen} onConfirm={onChange} />
		</div>
	);
};

export {LogoField};
