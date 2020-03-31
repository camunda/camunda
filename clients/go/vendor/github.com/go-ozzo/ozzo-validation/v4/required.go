// Copyright 2016 Qiang Xue. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found in the LICENSE file.

package validation

var (
	// ErrRequired is the error that returns when a value is required.
	ErrRequired = NewError("validation_required", "cannot be blank")
	// ErrNilOrNotEmpty is the error that returns when a value is not nil and is empty.
	ErrNilOrNotEmpty = NewError("validation_nil_or_not_empty_required", "cannot be blank")
)

// Required is a validation rule that checks if a value is not empty.
// A value is considered not empty if
// - integer, float: not zero
// - bool: true
// - string, array, slice, map: len() > 0
// - interface, pointer: not nil and the referenced value is not empty
// - any other types
var Required = requiredRule{err: ErrRequired, skipNil: false, condition: true}

// NilOrNotEmpty checks if a value is a nil pointer or a value that is not empty.
// NilOrNotEmpty differs from Required in that it treats a nil pointer as valid.
var NilOrNotEmpty = requiredRule{err: ErrNilOrNotEmpty, skipNil: true, condition: true}

type requiredRule struct {
	condition bool
	skipNil   bool
	err       Error
}

// Validate checks if the given value is valid or not.
func (r requiredRule) Validate(value interface{}) error {
	if r.condition {
		value, isNil := Indirect(value)
		if r.skipNil && !isNil && IsEmpty(value) || !r.skipNil && (isNil || IsEmpty(value)) {
			return r.err
		}
	}
	return nil
}

// When sets the condition that determines if the validation should be performed.
func (r requiredRule) When(condition bool) requiredRule {
	r.condition = condition
	return r
}

// Error sets the error message for the rule.
func (r requiredRule) Error(message string) requiredRule {
	r.err = r.err.SetMessage(message)
	return r
}

// ErrorObject sets the error struct for the rule.
func (r requiredRule) ErrorObject(err Error) requiredRule {
	r.err = err
	return r
}
