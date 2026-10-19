/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	Button,
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
	Separator,
} from '@camunda/design-system';
import {ArrowLeft} from '@camunda/design-system/icons';
import {Link} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useTranslation} from 'react-i18next';
import {ACCENT_COLOR_OPTIONS, BASE_COLOR_OPTIONS, PRIMARY_COLOR_OPTIONS} from '#/tasklist/modules/theme-editor/presets';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';
import {ColorField} from './ColorField';
import {ExportDialog} from './ExportDialog';
import {LogoField} from './LogoField';
import {RadiusSlider} from './RadiusSlider';
import {Section} from './Section';

type Props = {
	isResetDialogOpen: boolean;
	onResetDialogOpenChange: (open: boolean) => void;
};

const Customizer: React.FC<Props> = observer(({isResetDialogOpen, onResetDialogOpenChange}) => {
	const {t} = useTranslation();
	const {config, isDefault, hasUnappliedChanges, setConfig, setOverride, clearOverride, applyTheme, reset} =
		themeEditorStore;

	return (
		<Card size="sm" className="flex max-h-full min-h-0 flex-col self-start">
			<CardHeader>
				<Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 w-fit">
					<Link to="/tasklist">
						<ArrowLeft aria-hidden />
						{t('tasklist.themeEditorBack')}
					</Link>
				</Button>
				<CardTitle>{t('tasklist.themeEditorTitle')}</CardTitle>
				<CardDescription>{t('tasklist.themeEditorDescription')}</CardDescription>
			</CardHeader>
			{/* The vertical padding leaves room for the focus ring, which the scroll area would clip. */}
			<CardContent className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto py-1">
				<LogoField onChange={(logo) => setConfig({logo})} />
				<Separator className="my-1" />
				<div className="flex flex-col gap-2">
					<ColorField
						label={t('tasklist.themeEditorBase')}
						hexLabel={t('tasklist.themeEditorHexLabel', {color: t('tasklist.themeEditorBase')})}
						customLabel={t('tasklist.themeEditorCustom')}
						value={config.base}
						options={BASE_COLOR_OPTIONS}
						onSelect={(base) => setConfig({base})}
						onPreview={(base) => setOverride({base})}
						onPreviewEnd={clearOverride}
					/>
					<ColorField
						label={t('tasklist.themeEditorPrimary')}
						hexLabel={t('tasklist.themeEditorHexLabel', {color: t('tasklist.themeEditorPrimary')})}
						customLabel={t('tasklist.themeEditorCustom')}
						value={config.primary}
						options={PRIMARY_COLOR_OPTIONS}
						onSelect={(primary) => setConfig({primary})}
						onPreview={(primary) => setOverride({primary})}
						onPreviewEnd={clearOverride}
					/>
					<ColorField
						label={t('tasklist.themeEditorAccent')}
						hexLabel={t('tasklist.themeEditorHexLabel', {color: t('tasklist.themeEditorAccent')})}
						customLabel={t('tasklist.themeEditorCustom')}
						value={config.accent}
						options={ACCENT_COLOR_OPTIONS}
						onSelect={(accent) => setConfig({accent})}
						onPreview={(accent) => setOverride({accent})}
						onPreviewEnd={clearOverride}
					/>
				</div>
				<Separator className="my-1" />
				<Section title={t('tasklist.themeEditorRadius')}>
					<RadiusSlider
						label={t('tasklist.themeEditorRadius')}
						value={config.radius}
						onChange={(radius) => setConfig({radius})}
					/>
				</Section>
			</CardContent>
			<CardFooter className="flex flex-col gap-2 pb-3!">
				<Button
					type="button"
					variant="secondary"
					className="w-full"
					disabled={isDefault}
					onClick={() => onResetDialogOpenChange(true)}
				>
					{t('tasklist.themeEditorResetTheme')}
				</Button>
				<ExportDialog />
				<Button type="button" className="w-full" disabled={!hasUnappliedChanges} onClick={applyTheme}>
					{t('tasklist.themeEditorApply')}
				</Button>
			</CardFooter>
			<AlertDialog open={isResetDialogOpen} onOpenChange={onResetDialogOpenChange}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('tasklist.themeEditorResetTitle')}</AlertDialogTitle>
						<AlertDialogDescription>{t('tasklist.themeEditorResetDescription')}</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('tasklist.themeEditorCancel')}</AlertDialogCancel>
						<AlertDialogAction onClick={reset}>{t('tasklist.themeEditorReset')}</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</Card>
	);
});

export {Customizer};
