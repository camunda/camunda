/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {X} from '@camunda/design-system/icons';
import {Button, Dialog, DialogContent, DialogHeader, DialogTitle} from '@camunda/design-system';

type Props = {
	title: string;
	size?: 'sm' | 'md';
	onClose: () => void;
	children: React.ReactNode;
};

const ClusterVariableDialog: React.FC<Props> = ({title, size = 'md', onClose, children}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size={size}
				showCloseButton={false}
				aria-label={title}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-2 right-2"
						aria-label={t('admin.clusterVariables.closeButton')}
						onClick={onClose}
					>
						<X aria-hidden />
					</Button>
				</DialogHeader>
				{children}
			</DialogContent>
		</Dialog>
	);
};

export {ClusterVariableDialog};
