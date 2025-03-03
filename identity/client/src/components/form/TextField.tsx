import { ChangeEvent, FC, useId } from "react";
import { PasswordInput, TextArea, TextInput } from "@carbon/react";
import useTranslate from "src/utility/localization";

export type TextFieldProps = {
  label: string;
  value: string;
  errors?: string[];
  helperText?: string;
  placeholder?: string;
  cols?: number;
  autoFocus?: boolean;
  type?: "text" | "password" | "email";
  onBlur?: () => void;
  readOnly?: boolean;
  onChange?: (newValue: string) => void;
  maxCount?: number;
  enableCounter?: boolean;
  counterMode?: "character" | "word";
};

const TextField: FC<TextFieldProps> = ({
  onChange,
  onBlur,
  errors = [],
  value,
  helperText,
  placeholder,
  label,
  cols,
  autoFocus = false,
  type = "text",
  readOnly,
  maxCount = 255,
  enableCounter = false,
  counterMode = "character",
}) => {
  const { t } = useTranslate();
  const fieldId = useId();

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange?.(e.currentTarget.value);
  };
  const InputComponent =
    type === "password"
      ? PasswordInput
      : cols && cols > 1
        ? TextArea
        : TextInput;

  return (
    <InputComponent
      labelText={label}
      title={label}
      id={fieldId}
      helperText={helperText}
      value={value}
      placeholder={placeholder}
      onChange={handleChange}
      invalid={errors && errors.length > 0}
      invalidText={errors?.map((e) => t(e)).join(" ")}
      type={type}
      onBlur={onBlur}
      readOnly={readOnly}
      maxCount={maxCount}
      enableCounter={enableCounter}
      counterMode={counterMode}
      {...(autoFocus && { "data-modal-primary-focus": true })}
    />
  );
};

export default TextField;
