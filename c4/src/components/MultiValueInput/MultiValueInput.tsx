import { ComponentPropsWithoutRef } from "react"
import { usePrefix } from "@carbon/react"
import classnames from "classnames"
import { Close } from "@carbon/icons-react"

import { useId } from "../../hooks"

import "./MultiValueInput.scss"

type Value = {
	value: string
	label?: string
	invalid?: boolean
}

interface MultiValueInputProps extends ComponentPropsWithoutRef<"input"> {
	values: Value[]
	onRemove: (value: string, index: number) => void
	tagButtonTitle?: string
	invalid?: boolean
	titleText?: string
	invalidText?: string
	helperText?: string
}

export default function MultiValueInput({
	values,
	onRemove,
	tagButtonTitle,
	invalid,
	titleText,
	invalidText,
	helperText,
	...props
}: MultiValueInputProps) {
	const prefix = usePrefix()
	const id = useId()

	if(!titleText && !props.placeholder && !props["aria-label"]) {
		console.error("MultiValueInput: Must provide either a titleText, placeholder, or aria-label.")
	}

	return (
		<div className="MultiValueInput">
			{titleText && (
				<label id={id} className={`${prefix}--label`}>
					{titleText}
				</label>
			)}
			<div
				className={classnames(
					"textInput",
					`${prefix}--text-input`,
					`${prefix}--text-input__field-wrapper`,
					{
						[`${prefix}--text-input--invalid`]: invalid,
					},
				)}
				data-invalid={invalid}
			>
				{values?.map(({ value, label, invalid }, i) => {
					return (
						<RemovableTag
							key={i + value}
							onRemove={() => onRemove(value, i)}
							title={label || value}
							isInvalid={invalid}
							buttonTitle={tagButtonTitle}
						/>
					)
				})}
				<input
					{...props}
					aria-labelledby={titleText ? id : undefined}
					className="textInput"
				/>
			</div>
			{helperText && !invalid && (
				<div className={`${prefix}--form__helper-text`}>{helperText}</div>
			)}
			{invalid && invalidText && (
				<div className={`${prefix}--form-requirement`}>{invalidText}</div>
			)}
		</div>
	)
}

function RemovableTag({
	onRemove,
	title,
	isInvalid,
	buttonTitle,
}: {
	onRemove: () => void
	title: string
	isInvalid?: boolean
	buttonTitle?: string
}) {
	const prefix = usePrefix()

	return (
		<div
			className={classnames(
				"RemovableTag",
				`${prefix}--tag`,
				`${prefix}--tag--filter`,
				{
					[`${prefix}--tag--high-contrast`]: !isInvalid,
					[`${prefix}--tag--red`]: isInvalid,
				},
			)}
		>
			<span className={`${prefix}--tag__label`} title={title}>
				{title}
			</span>
			<button
				aria-label={buttonTitle}
				className={`${prefix}--tag__close-icon`}
				onClick={onRemove}
				title={buttonTitle}
				type="button"
			>
				<Close />
			</button>
		</div>
	)
}
