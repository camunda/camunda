import { ChangeEvent, FC } from "react";
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
  type?: "text" | "password";
} & (
  | {
      onChange: (newValue: string) => void;
      disabled?: false;
    }
  | {
      onChange?: () => void;
      disabled: true;
    }
);

const TextField: FC<TextFieldProps> = ({
  onChange = () => null,
  disabled = false,
  errors,
  value,
  helperText,
  placeholder,
  label,
  cols,
  autoFocus = false,
  type = "text",
}) => {
  const { t } = useTranslate();
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange(e.currentTarget.value);
  };
  const InputComponent =
    type === "password"
      ? PasswordInput
      : cols && cols > 1
        ? TextArea
        : TextInput;

  const additionalProps = autoFocus
    ? {
        "data-modal-primary-focus": true,
      }
    : {};

  return (
    <InputComponent
      labelText={label}
      title={label}
      id={label}
      helperText={helperText}
      value={value}
      disabled={disabled}
      placeholder={placeholder}
      onChange={handleChange}
      invalid={errors && errors.length > 0}
      invalidText={errors?.map((e) => t(e)).join(" ")}
      {...additionalProps}
    />
  );
};

export default TextField;
