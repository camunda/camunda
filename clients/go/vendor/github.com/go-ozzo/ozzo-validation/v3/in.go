// Copyright 2016 Qiang Xue. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found in the LICENSE file.

package validation

import (
	"errors"
	"reflect"
)

// In returns a validation rule that checks if a value can be found in the given list of values.
// reflect.DeepEqual() will be used to determine if two values are equal.
// For more details please refer to https://golang.org/pkg/reflect/#DeepEqual
// An empty value is considered valid. Use the Required rule to make sure a value is not empty.
func In(values ...interface{}) InRule {
	return InRule{
		elements: values,
		message:  "must be a valid value",
	}
}

// InRule is a validation rule that validates if a value can be found in the given list of values.
type InRule struct {
	elements []interface{}
	message  string
}

// Validate checks if the given value is valid or not.
func (r InRule) Validate(value interface{}) error {
	value, isNil := Indirect(value)
	if isNil || IsEmpty(value) {
		return nil
	}

	for _, e := range r.elements {
		if reflect.DeepEqual(e, value) {
			return nil
		}
	}
	return errors.New(r.message)
}

// Error sets the error message for the rule.
func (r InRule) Error(message string) InRule {
	r.message = message
	return r
}
