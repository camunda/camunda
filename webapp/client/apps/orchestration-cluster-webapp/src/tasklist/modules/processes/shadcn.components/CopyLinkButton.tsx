/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {useActorRef, useSelector} from '@xstate/react';
import {Check, Link as LinkIcon} from 'lucide-react';
import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {assign, fromPromise, setup} from 'xstate';

const copyTextLogic = fromPromise<void, {textToCopy: string}>(async ({input}) => {
	await navigator.clipboard.writeText(input.textToCopy);
});

const copyMachine = setup({
	types: {
		context: {} as {
			textToCopy: string;
		},
		events: {} as {
			type: 'copy';
			textToCopy: string;
		},
	},
	actors: {
		copyText: copyTextLogic,
	},
	actions: {
		storeTextToCopy: assign(({event}) => ({textToCopy: event.textToCopy})),
		logCopyError: (_, params: {error: unknown}) => {
			console.error('Failed to copy URL to clipboard', params.error);
		},
	},
	delays: {
		RESET_COPY_STATE_DELAY: 2000,
	},
}).createMachine({
	context: {textToCopy: ''},
	initial: 'Idle',
	states: {
		Idle: {
			on: {
				copy: {
					target: 'Copying',
					actions: 'storeTextToCopy',
				},
			},
		},
		Copying: {
			invoke: {
				src: 'copyText',
				input: ({context}) => ({textToCopy: context.textToCopy}),
				onDone: {target: 'Copied'},
				onError: {
					target: 'Idle',
					actions: {
						type: 'logCopyError',
						params: ({event}) => ({error: event.error}),
					},
				},
			},
		},
		Copied: {
			after: {
				RESET_COPY_STATE_DELAY: {target: 'Idle'},
			},
			on: {
				copy: {
					target: 'Copying',
					actions: 'storeTextToCopy',
				},
			},
		},
	},
});

type Props = {
	textToCopy: string;
};

const CopyLinkButton: React.FC<Props> = ({textToCopy}) => {
	const {t} = useTranslation();
	const actorRef = useActorRef(copyMachine);
	const isCopied = useSelector(actorRef, (snapshot) => snapshot.matches('Copied'));

	const handleCopyClick = useCallback(() => {
		actorRef.send({type: 'copy', textToCopy});
	}, [textToCopy, actorRef]);

	return (
		<Button type="button" variant="ghost" size="sm" className="sm:mr-auto" onClick={handleCopyClick}>
			{isCopied ? <Check aria-hidden /> : <LinkIcon aria-hidden />}
			{isCopied
				? t('tasklist.processesStartProcessWithFormCopyURLButtonLabel')
				: t('tasklist.processesStartProcessWithFormShareButtonLabel')}
		</Button>
	);
};

export {CopyLinkButton};
