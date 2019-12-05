package validation

import (
	"errors"
	"fmt"
	"reflect"
)

// MultipleOf returns a validation rule that checks if a value is a multiple of the "base" value.
// Note that "base" should be of integer type.
func MultipleOf(base interface{}) MultipleOfRule {
	return MultipleOfRule{
		base,
		fmt.Sprintf("must be multiple of %v", base),
	}
}

// MultipleOfRule is a validation rule that checks if a value is a multiple of the "base" value.
type MultipleOfRule struct {
	base    interface{}
	message string
}

// Error sets the error message for the rule.
func (r MultipleOfRule) Error(message string) MultipleOfRule {
	r.message = message
	return r
}

// Validate checks if the value is a multiple of the "base" value.
func (r MultipleOfRule) Validate(value interface{}) error {
	rv := reflect.ValueOf(r.base)
	switch rv.Kind() {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		v, err := ToInt(value)
		if err != nil {
			return err
		}
		if v%rv.Int() == 0 {
			return nil
		}

	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64, reflect.Uintptr:
		v, err := ToUint(value)
		if err != nil {
			return err
		}

		if v%rv.Uint() == 0 {
			return nil
		}
	default:
		return fmt.Errorf("type not supported: %v", rv.Type())
	}

	return errors.New(r.message)
}
