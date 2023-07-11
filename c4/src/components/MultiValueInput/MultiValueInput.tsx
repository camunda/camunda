import { ComponentPropsWithoutRef } from "react"
import { usePrefix } from "@carbon/react"
import classnames from "classnames"
import { Close } from "@carbon/icons-react"

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
}

export default function MultiValueInput({
	values,
	onRemove,
	tagButtonTitle,
	invalid,
	titleText,
	invalidText,
	...props
}: MultiValueInputProps) {
	const prefix = usePrefix()

	return (
		<div className="MultiValueInput">
			{titleText && <label className={`${prefix}--label`}>{titleText}</label>}
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
				<input {...props} className="textInput" />
			</div>
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
